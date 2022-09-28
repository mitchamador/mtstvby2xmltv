package by.mitchamador.tvmtsby2xmltv.objects;

import java.util.List;

public class MtsTvChannel {
    private final String id;
    private String title;
    private String thumbnailUrl;
    private String playUrl;
    private boolean bought;
    private List<MtsTvProgram> programs;

    public MtsTvChannel(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getPlayUrl() {
        return playUrl;
    }

    public void setPlayUrl(String playUrl) {
        this.playUrl = playUrl;
    }

    public boolean isBought() {
        return bought;
    }

    public void setBought(boolean bought) {
        this.bought = bought;
    }

    public List<MtsTvProgram> getPrograms() {
        return programs;
    }

    public void setPrograms(List<MtsTvProgram> programs) {
        this.programs = programs;
    }
}
