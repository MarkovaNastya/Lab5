package lab5;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;

import java.util.HashMap;
import java.util.Map;

public class ActorData extends AbstractActor {

    HashMap<String, Map<Integer, Integer>> data = new HashMap<>();

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(
                        GetMsg.class, getMsg -> {
                            String url = getMsg.getUrl();
                            Integer count = getMsg.getCount();

                            if (data.containsKey(url) && data.get(url).containsKey(count)) {
                                getSender().tell(data.get(url).get(count), ActorRef.noSender());
                            } else {
                                getSender().tell(-1, ActorRef.noSender());
                            }

                        }
                )
                .match(
                        PutMsg.class, putMsg -> {
                            String url = putMsg.getUrl();
                            Map<Integer, Integer> info;
                            if (data.containsKey(url)) {
                                info = data.get(url);
                            } else {
                                info = new HashMap<>();
                                info.put(putMsg.getCount(), putMsg.getTime());
                            }
                            data.put(url, info);
                        }
                )
                .build();
    }
}
