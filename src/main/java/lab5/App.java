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

import static org.asynchttpclient.Dsl.asyncHttpClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;


public class App {

    private final static String WELCOME_MESSAGE = "start!";
    private final static String SERVER_ONLINE_MESSAGE = "Server online at http://localhost:8080/\nPress RETURN to stop...";

    private final static String ROUTES = "routes";
    private final static String LOCALHOST = "locahost";
    private final static int LOCALHOST_PORT = 8080;

    private final static String TEST_URL_PARAM = "testUrl";
    private final static String COUNT_PARAM = "count";
    private final static String EMPTY_STRING_PARAM = "";
    private final static String AVERAGE_DELAY = "Average delay ";
    private final static int TIME_DURATION_MILLS = 5000;
    private final static int PARALLELISM_TRUE = 1;
    private final static int ZERO = 0;
    private final static int RESULT_NOT_YET_COUNTED = -1;


    public static void main(String[] args) throws IOException {
        System.out.println(WELCOME_MESSAGE);
        ActorSystem system = ActorSystem.create(ROUTES);

        ActorRef dataActor = system.actorOf(Props.create(ActorData.class));


        final Http http = Http.get(system);
        final ActorMaterializer materializer = ActorMaterializer.create(system);

        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = Flow.of(HttpRequest.class).map(
                req -> {

                    String url = req.getUri().query().get(TEST_URL_PARAM).orElse(EMPTY_STRING_PARAM);
                    String count = req.getUri().query().get(COUNT_PARAM).orElse(EMPTY_STRING_PARAM);

                    Integer countInteger = Integer.parseInt(count);

                    Pair<String, Integer> reqInfo = new Pair<>(url, countInteger);

                    Source<Pair<String, Integer>, NotUsed> source = Source.from(Collections.singletonList(reqInfo));

                    Flow<Pair<String, Integer>, HttpResponse, NotUsed>  testSink = Flow.<Pair<String, Integer>>create()
                            .map(pair -> new Pair<>(HttpRequest.create().withUri(pair.first()), pair.second()))
                            .mapAsync(PARALLELISM_TRUE, pair -> {
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
                                    if ((int)actorAnswer != RESULT_NOT_YET_COUNTED) {
                                        return CompletableFuture.completedFuture((int)actorAnswer);
                                    }

                                    Sink<CompletionStage<Long>, CompletionStage<Integer>> fold = Sink
                                            .fold(ZERO, (ac, el) -> {
                                                int testEl = (int) (ZERO + el.toCompletableFuture().get());
                                                return ac + testEl;
                                            });

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
                                                                        })
                                                                );
                                                    })
                                                    .toMat(fold, Keep.right()),
                                                    Keep.right()
                                            )
                                            .run(materializer);
                                }).thenCompose(sum -> {
                                    Patterns.ask(
                                            dataActor,
                                            new PutMsg(
                                                    new javafx.util.Pair<>(
                                                            reqInfo.first(),
                                                            new javafx.util.Pair<>(
                                                                    reqInfo.second(),
                                                                    sum
                                                            )
                                                    )
                                            ),
                                            TIME_DURATION_MILLS
                                    );
                                    Double averageTime = (double) (sum / countInteger);
                                    return CompletableFuture.completedFuture(HttpResponse.create()
                                            .withEntity(AVERAGE_DELAY + averageTime.toString()));
                                        }
                                );
                            });

                    CompletionStage<HttpResponse> result = source.via(testSink)
                            .toMat(Sink.last(), Keep.right())
                            .run(materializer);
                    return result.toCompletableFuture().get();
                }
        );


        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                routeFlow,
                ConnectHttp.toHost(LOCALHOST, LOCALHOST_PORT),
                materializer
        );

        System.out.println(SERVER_ONLINE_MESSAGE);
        System.in.read();

        binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> system.terminate());
        // and shutdown when done
    }
}
