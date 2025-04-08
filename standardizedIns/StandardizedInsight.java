package standardizedIns;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StandardizedInsight {
    private String standardizedText;
    private int companyCount;
    private List<String> companies;
}