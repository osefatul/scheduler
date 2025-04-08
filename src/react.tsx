import React, { useEffect, useState } from 'react';
import { useGetStandardizedInsightsQuery } from '@/internal/services/insightAPI';
import { StandardizedInsight } from '@/internal/services/insightAPI';
import { Spinner } from "../spinner";

interface StandardizedInsightsProps {
  insightType: string;
  insightSubType: string;
}

const StandardizedInsights: React.FC<StandardizedInsightsProps> = ({ 
  insightType, 
  insightSubType 
}) => {
  // Only query if both type and subtype are provided
  const { 
    data: standardizedInsights, 
    isLoading, 
    error 
  } = useGetStandardizedInsightsQuery(
    { insightType, insightSubType },
    { skip: !insightType || !insightSubType }
  );

  if (isLoading) {
    return <Spinner />;
  }

  if (error) {
    return <div>Error loading standardized insights.</div>;
  }

  if (!standardizedInsights?.length) {
    return <div>No standardized insights found for this type and subtype.</div>;
  }

  return (
    <div className="standardized-insights">
      <h3>Standardized Insights</h3>
      <p>Showing unique insight templates grouped by pattern</p>

      <div className="insights-list">
        {standardizedInsights.map((item, index) => (
          <div key={index} className="insight-card">
            <div className="insight-template">
              <h4>Template {index + 1}</h4>
              <p>{item.standardizedText}</p>
            </div>
            <div className="insight-stats">
              <span className="company-count">
                <strong>{item.companyCount}</strong> {item.companyCount === 1 ? 'Company' : 'Companies'}
              </span>
            </div>
            <div className="company-list">
              <h5>Applies to:</h5>
              <ul>
                {item.companies.slice(0, 5).map((company, idx) => (
                  <li key={idx}>{company}</li>
                ))}
                {item.companies.length > 5 && (
                  <li>...and {item.companies.length - 5} more</li>
                )}
              </ul>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default StandardizedInsights;