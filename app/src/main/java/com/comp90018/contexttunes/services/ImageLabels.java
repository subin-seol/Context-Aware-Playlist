package com.comp90018.contexttunes.services;

import java.util.ArrayList;
import java.util.List;

public class ImageLabels {
    public static class LabelConfidence {
        private String label;
        private float confidence;

        public LabelConfidence(String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
        }

        public String getLabel() {
            return label;
        }

        public float getConfidence() {
            return confidence;
        }
    }

    private List<LabelConfidence> items;

    public ImageLabels() {
        this.items = new ArrayList<>();
    }

    public void addLabel(String label, float confidence) {
        items.add(new LabelConfidence(label, confidence));
    }

    public List<LabelConfidence> getItems() {
        return items;
    }
}