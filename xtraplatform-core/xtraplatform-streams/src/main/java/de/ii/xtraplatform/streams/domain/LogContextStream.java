package de.ii.xtraplatform.streams.domain;

import akka.NotUsed;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.RunnableGraph;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class LogContextStream {

    public static <T,U> Source<T, U> withMdc(Source<T, U> source) {
      Map<String, String> mdc = MDC.getCopyOfContextMap();

      if (Objects.nonNull(mdc)) {
        Flow<T, T, NotUsed> mdcFlow = Flow.fromFunction((Function<T, T>) t -> {
          MDC.setContextMap(mdc);
          return t;
        });
        return source.via(mdcFlow);
      }
      return source;
    }

    public static <T,U,V> Function2<T, U, V> withMdc(Function2<T, U, V> function) {
      Map<String, String> mdc = MDC.getCopyOfContextMap();

      if (Objects.nonNull(mdc)) {
        return (Function2<T, U, V>) (t,u) -> {
          MDC.setContextMap(mdc);
          return function.apply(t,u);
        };
      }
      return function;
    }

    public static <T, U, V> RunnableGraphWithMdc<CompletionStage<V>> graphWithMdc(Source<T, U> source, Sink<T, CompletionStage<V>> sink) {
      return graphWithMdc(source, sink, Keep.right());
    }

    public static <T, U, V, W> RunnableGraphWithMdc<CompletionStage<W>> graphWithMdc(Source<T, U> source, Sink<T, CompletionStage<V>> sink, Function2<U,CompletionStage<V>,CompletionStage<W>> combiner) {
      return () -> withMdc(source).toMat(sink, combiner);
    }
}
