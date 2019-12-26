package lab5;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.japi.Pair;
import akka.util.ByteString;

import static org.asynchttpclient.Dsl.asyncHttpClient;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;


import static akka.actor.TypedActor.context;

public class App {

    private final static int TIME_DURATION_MILLS = 5000;

    public static void main(String[] args) throws IOException {
        System.out.println("start!");
        ActorSystem system = ActorSystem.create("routes");

        Props props;
        ActorRef dataActor = system.actorOf(Props.create(ActorData.class));


        final Http http = Http.get(system);
        final ActorMaterializer materializer = ActorMaterializer.create(system);

        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = Flow.of(HttpRequest.class).map(
                req -> {

                    String url = req.getUri().query().get("testUrl").orElse("");
                    String count = req.getUri().query().get("count").orElse("");

                    if (url.isEmpty()) {

                    }
                    if (count.isEmpty()) {

                    }

                    Integer countInteger = Integer.parseInt(count);

                    Pair<String, Integer> reqInfo = new Pair<>(url, countInteger);

                    Source<Pair<String, Integer>, NotUsed> source = Source.from(Collections.singletonList(reqInfo));

                    Flow<Pair<String, Integer>, HttpResponse, NotUsed>  testSink = Flow.<Pair<String, Integer>>create()
                            .map(pair -> new Pair<>(HttpRequest.create().withUri(pair.first()), pair.second()))
                            .mapAsync(1, pair -> {
                                return Patterns.ask(
                                        dataActor,
                                        new GetMsg(
                                                new javafx.util.Pair<>(
                                                        reqInfo.first(),
                                                        reqInfo.second()
                                                )
                                        ),
                                        Duration.ofMillis(TIME_DURATION_MILLS)
                                ).thenCompose(actorAnswer -> {
                                    if ((int)actorAnswer != -1) {
                                        return CompletableFuture.completedFuture((int)actorAnswer);
                                    }

                                    return Source.from(Collections.singletonList(pair))
                                            .toMat(
                                                    Flow.<Pair<HttpRequest, Integer>>create()
                                                    .mapConcat(p ->
                                                            Collections.nCopies(
                                                                    p.second(),
                                                                    p.first()
                                                            )
                                                    )
                                                    .mapAsync(1, req2 -> {
                                                        return CompletableFuture.supplyAsync( () -> System.currentTimeMillis())
                                                                .thenCompose(startTime ->
                                                                        CompletableFuture.supplyAsync( () -> {
                                                                            CompletionStage<Long> whenResponse = asyncHttpClient()
                                                                                    .prepareGet(req2.getUri().toString())
                                                                                    .execute()
                                                                                    .toCompletableFuture()
                                                                                    .thenCompose(answer -> CompletableFuture.completedFuture(System.currentTimeMillis() - startTime));
                                                                            return whenResponse;
                                                                        }))
                                                    })
                                            )
                                }
                                )
                            })






                }
        );
        //<вызов метода которому передаем Http, ActorSystem и ActorMaterializer>;


        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                routeFlow,
                ConnectHttp.toHost("localhost", 8080),
                materializer
        );

        System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
        System.in.read();

        binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> system.terminate());
        // and shutdown when done
    }
}
