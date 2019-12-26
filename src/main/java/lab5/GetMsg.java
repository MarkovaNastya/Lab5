package lab5;

import javafx.util.Pair;

public class GetMsg {

    private Pair<String, Integer> pair;

    public GetMsg(Pair<String, Integer> pair) {
        this.pair = pair;
    }

    public String getUrl() {
        return pair.getKey();
    }

    public Integer getCount() {
        return pair.getValue();
    }


}
