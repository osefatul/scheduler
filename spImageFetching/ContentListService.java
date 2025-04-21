package com.usbank.corp.dcr.api.service;

import com.microsoft.graph.models.ListItem;
import com.microsoft.graph.requests.GraphServiceClient;
import com.usbank.corp.dcr.api.dto.ContentListDTO;
import com.usbank.corp.dcr.api.exception.GenericException;
import com.usbank.corp.dcr.api.util.ApplicationConstants;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ContentListService {

    @Value("${graph.site.id}")
    private String siteId;

    @Value("${graph.contentList.id}")
    private String listId;

    @Autowired
    private GraphServiceClient<Request> graphClient;

    /**
     * Fetches banner content items from the configured SharePoint list.
     */
    public List<ContentListDTO> getBannerContents() {
        long startTime = System.currentTimeMillis();
        List<ListItem> items;

        try {
            log.info("Fetching content list data from SharePoint");
            items = Objects.requireNonNull(
                graphClient
                  .sites().bySiteId(siteId)
                  .lists().byListId(listId)
                  .items()
                  .buildRequest(r -> {
                      r.queryParameters.select = new String[]{ ApplicationConstants.BANNER_CONTENT_FIELDS_SELECT };
                      r.queryParameters.expand = new String[]{ ApplicationConstants.BANNER_CONTENT_FIELDS_EXPAND };
                  })
                  .get()
            ).getValue();

            log.info("Successfully fetched {} items in {}ms",
                     items.size(),
                     System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("{} Unexpected error fetching content list: {}",
                      ApplicationConstants.SEVERITY2, e.getMessage(), e);
            throw new GenericException(
              HttpStatus.INTERNAL_SERVER_ERROR,
              ApplicationConstants.CMS_CONTENT_DATA_RETRIEVE_FAILED_ERROR_MESSAGE + " - " + e.getMessage()
            );
        }

        try {
            return items.stream()
                        .map(this::convertItemToResponse)
                        .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("{} Error converting items to DTO: {}",
                      ApplicationConstants.SEVERITY3, e.getMessage(), e);
            throw new GenericException(
              HttpStatus.INTERNAL_SERVER_ERROR,
              "Exception in converting content items to response"
            );
        }
    }

    /**
     * Converts a raw ListItem from Graph into your ContentListDTO,
     * parsing out the HTML and inlining any SharePoint images.
     */
    private ContentListDTO convertItemToResponse(ListItem item) {
        ContentListDTO dto = new ContentListDTO();
        dto.setId(item.getId());

        if (item.getFields() == null || item.getFields().getAdditionalData() == null) {
            log.warn("Item fields/additionalData is null for itemId={}", item.getId());
            return dto;
        }

        // parse out your HTML blob
        String html = item.getFields()
                          .getAdditionalData()
                          .get(ApplicationConstants.BANNER_CONTENT) != null
                     ? item.getFields().getAdditionalData()
                          .get(ApplicationConstants.BANNER_CONTENT).toString()
                     : "";

        Document doc = Jsoup.parse(html);
        String title   = doc.select(ApplicationConstants.HTML_TITLE_TAG).text();
        String message = doc.select(ApplicationConstants.HTML_MESSAGE_TAG).text();
        String imgSrc  = doc.select(ApplicationConstants.HTML_IMG_TAG)
                             .attr(ApplicationConstants.HTML_IMG_SRC);

        log.info("Raw image URL from HTML: {}", imgSrc);

        String bannerImageUrl;
        if (imgSrc.startsWith("http://") || imgSrc.startsWith("https://")) {
            bannerImageUrl = imgSrc;
        } else {
            // SharePoint‑hosted image: inline it
            bannerImageUrl = fetchAndEncodeSharePointImage(
                ApplicationConstants.SHAREPOINT_DOMAIN + imgSrc
            );
        }

        dto.setTitle(title);
        dto.setMessage(message);
        dto.setBannerImageUrl(bannerImageUrl);

        // location & backgroundColor are simple string fields
        dto.setLocation(
          Objects.toString(
            item.getFields().getAdditionalData()
                .get(ApplicationConstants.LOCATION_TYPE), ""
          )
        );
        dto.setBannerBackgroundColor(
          Objects.toString(
            item.getFields().getAdditionalData()
                .get(ApplicationConstants.BACKGROUND_COLOR), ""
          )
        );

        return dto;
    }

    /**
     * Fetches the file at the given absolute URL path (assumed to be
     * under /sites/{siteName}/…), reads its bytes, and returns a
     * data‑URI base64 string.
     */
    private String fetchAndEncodeSharePointImage(String absoluteUrl) {
        try {
            // strip off the site root and leave the drive‑relative path:
            // "/sites/{siteName}/" → length = "/sites/".length() + siteName.length() + 1
            String path = absoluteUrl.substring(
                absoluteUrl.indexOf("/sites/") + "/sites/".length()
            );
            // now remove the leading "{siteName}/"
            int slash = path.indexOf('/');
            if (slash >= 0) {
                path = path.substring(slash + 1);
            }

            // get the site’s default document library drive
            String driveId = graphClient
              .sites().bySiteId(siteId)
              .drive()
              .buildRequest()
              .get()
              .getId();

            // fetch the file bytes
            InputStream in = graphClient
              .drives().byDriveId(driveId)
              .root()
              .itemWithPath(path)
              .content()
              .buildRequest()
              .get();

            byte[] bytes = in.readAllBytes();
            // pick the prefix based on file extension
            String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
            String prefix;
            switch (ext) {
                case "png":   prefix = "data:image/png;base64,";   break;
                case "jpg":
                case "jpeg":  prefix = "data:image/jpeg;base64,";  break;
                case "gif":   prefix = "data:image/gif;base64,";   break;
                default:      prefix = "data:image/*;base64,";     break;
            }

            return prefix + Base64.getEncoder().encodeToString(bytes);

        } catch (Exception e) {
            log.error("Error fetching or encoding SharePoint image `{}`: {}",
                      absoluteUrl, e.getMessage(), e);
            // fallback: return the original URL so the <img> still works
            return absoluteUrl;
        }
    }
}
