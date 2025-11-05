package com.comp90018.contexttunes.domain;

import android.graphics.Bitmap;
import com.comp90018.contexttunes.utils.ImageLabelsHasher;

import java.util.ArrayList;
import java.util.List;

public class ImageLabels {
    public static class LabelConfidence {
        private String label;
        private float confidence;
        public LabelConfidence(String label, float confidence) {
            this.label = label; this.confidence = confidence;
        }
        public String getLabel() { return label; }
        public float getConfidence() { return confidence; }
    }

    private String sourceHash; // md5 of bitmap bytes or uri string
    private List<LabelConfidence> items;

    public ImageLabels() { this.items = new ArrayList<>(); }

    public void addLabel(String label, float confidence) {
        items.add(new LabelConfidence(label, confidence));
    }

    public List<LabelConfidence> getItems() { return items; }

    public void setSourceHash(String h) { this.sourceHash = h; }
    public String getSourceHash() { return sourceHash; }

    public boolean matchesCurrentImage(Bitmap bmp) {
        return sourceHash != null && sourceHash.equals(ImageLabelsHasher.hash(bmp));
    }

    /** Optional convenience for caller-side equality */
    public static boolean hashEquals(Bitmap bmp, String hash) {
        return hash != null && hash.equals(ImageLabelsHasher.hash(bmp));
    }
}