/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.htrace.core;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Base interface for gathering and reporting statistics about a block of
 * execution.
 *
 * <p>Spans should form a directed acyclic graph structure.  It should be
 * possible to keep following the parents of a span until you arrive at a
 * span with no parents.</p>
 */
@JsonSerialize(using = Span.SpanSerializer.class)
public interface Span {
  /**
   * The block has completed, stop the clock
   */
  void stop();

  /**
   * Get the span start time.
   *
   * @return    The start time, in approximate milliseconds since the epoch.
   */
  long getStartTimeMillis();

  /**
   * Get the span stop time.
   *
   * @return    The stop time, in approximate milliseconds since the epoch.
   */
  long getStopTimeMillis();

  /**
   * Return the total amount of time elapsed since start was called, if running,
   * or difference between stop and start
   *
   * @return    The elapsed time in milliseconds.
   */
  long getAccumulatedMillis();

  /**
   * Has the span been started and not yet stopped?
   *
   * @return    True if the span is still running (has no stop time).
   */
  boolean isRunning();

  /**
   * Return a textual description of this span.
   *
   * @return    The description of this span.  Will never be null.
   */
  String getDescription();

  /**
   * A pseudo-unique (random) number assigned to this span instance.
   *
   * @return    The spanID.  This object is immutable and is safe to access
   *            from multiple threads.
   */
  SpanId getSpanId();

  /**
   * Create a child span of this span with the given description
   * @deprecated Since 4.0.0. Use {@link MilliSpan.Builder}
   * @return A new child span.
   */
  @Deprecated
  Span child(String description);

  @Override
  String toString();

  /**
   * Returns the parent IDs of the span.
   *
   * @return The array of parents, or an empty array if there are no parents.
   */
  SpanId[] getParents();

  /**
   * Set the parents of this span.
   *
   * <p>Any existing parents will be cleared by this call.</p>
   *
   * @param parents     The parents to set.
   */
  void setParents(SpanId[] parents);

  /**
   * Add a data annotation associated with this span
   *
   * @param key         The key to set.
   * @param value       The value to set.
   */
  void addKVAnnotation(String key, String value);

  /**
   * Add a timeline annotation associated with this span
   *
   * @param msg         The annotation to set.  It will be associated with
   *                    the current time.
   */
  void addTimelineAnnotation(String msg);

  /**
   * Get the key-value annotations associated with this span.
   *
   * @return            The annotation map in read-only form.
   *                    Will never be null.
   */
  Map<String, String> getKVAnnotations();

  /**
   * Get the timeline annotation list.
   *
   * @return            The annotation list in read-only form.
   *                    Will never be null.
   */
  List<TimelineAnnotation> getTimelineAnnotations();

  /**
   * Return a unique id for the process from which this Span originated.
   *
   * @return            The tracer id.  Will never be null.
   */
  String getTracerId();

  /**
   * Set the tracer id of a span.
   *
   * @param s           The tracer ID to set.
   */
  void setTracerId(String s);

  /**
   * Serialize to Json
   *
   * @return            A JSON string with the span data.
   */
  String toJson();

  public static class SpanSerializer extends JsonSerializer<Span> {
    @Override
    public void serialize(Span span, JsonGenerator jgen, SerializerProvider provider)
        throws IOException {
      jgen.writeStartObject();
      if (span.getSpanId().isValid()) {
        jgen.writeStringField("a", span.getSpanId().toString());
      }
      if (span.getStartTimeMillis() != 0) {
        jgen.writeNumberField("b", span.getStartTimeMillis());
      }
      if (span.getStopTimeMillis() != 0) {
        jgen.writeNumberField("e", span.getStopTimeMillis());
      }
      if (!span.getDescription().isEmpty()) {
        jgen.writeStringField("d", span.getDescription());
      }
      String tracerId = span.getTracerId();
      if (!tracerId.isEmpty()) {
        jgen.writeStringField("r", tracerId);
      }
      jgen.writeArrayFieldStart("p");
      for (SpanId parent : span.getParents()) {
        jgen.writeString(parent.toString());
      }
      jgen.writeEndArray();
      Map<String, String> traceInfoMap = span.getKVAnnotations();
      if (!traceInfoMap.isEmpty()) {
        jgen.writeObjectFieldStart("n");
        String[] keys = traceInfoMap.keySet().
            toArray(new String[traceInfoMap.size()]);
        Arrays.sort(keys);
        for (String key : keys) {
          jgen.writeStringField(key, traceInfoMap.get(key));
        }
        jgen.writeEndObject();
      }
      List<TimelineAnnotation> timelineAnnotations =
          span.getTimelineAnnotations();
      if (!timelineAnnotations.isEmpty()) {
        jgen.writeArrayFieldStart("t");
        for (TimelineAnnotation tl : timelineAnnotations) {
          jgen.writeStartObject();
          jgen.writeNumberField("t", tl.getTime());
          jgen.writeStringField("m", tl.getMessage());
          jgen.writeEndObject();
        }
        jgen.writeEndArray();
      }
      jgen.writeEndObject();
    }
  }
}
