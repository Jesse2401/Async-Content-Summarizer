package service;

import strategy.SummaryStrategy;
import strategy.HuggingFaceStrategy;

public class ContentSummarizerServiceImpl extends ContentSummarizerService {
    
    @Override
    protected SummaryStrategy getStrategy() {
        return new HuggingFaceStrategy();
    }
}

