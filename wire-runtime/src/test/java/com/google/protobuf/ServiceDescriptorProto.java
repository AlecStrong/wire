// Code generated by Wire protocol buffer compiler, do not edit.
// Source file: ../wire-runtime/src/test/proto/google/protobuf/descriptor.proto at 187:1
package com.google.protobuf;

import com.squareup.wire.Message;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.TagMap;
import com.squareup.wire.WireField;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.util.Collections;
import java.util.List;

/**
 * Describes a service.
 */
public final class ServiceDescriptorProto extends Message<ServiceDescriptorProto> {
  public static final ProtoAdapter<ServiceDescriptorProto> ADAPTER = ProtoAdapter.newMessageAdapter(ServiceDescriptorProto.class);

  private static final long serialVersionUID = 0L;

  public static final String DEFAULT_NAME = "";

  public static final String DEFAULT_DOC = "";

  @WireField(
      tag = 1,
      adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  public final String name;

  @WireField(
      tag = 2,
      adapter = "com.google.protobuf.MethodDescriptorProto#ADAPTER",
      label = WireField.Label.REPEATED
  )
  public final List<MethodDescriptorProto> method;

  /**
   * Doc string for generated code.
   */
  @WireField(
      tag = 4,
      adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  public final String doc;

  @WireField(
      tag = 3,
      adapter = "com.google.protobuf.ServiceOptions#ADAPTER"
  )
  public final ServiceOptions options;

  public ServiceDescriptorProto(String name, List<MethodDescriptorProto> method, String doc, ServiceOptions options) {
    this(name, method, doc, options, TagMap.EMPTY);
  }

  public ServiceDescriptorProto(String name, List<MethodDescriptorProto> method, String doc, ServiceOptions options, TagMap tagMap) {
    super(tagMap);
    this.name = name;
    this.method = immutableCopyOf(method);
    this.doc = doc;
    this.options = options;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) return true;
    if (!(other instanceof ServiceDescriptorProto)) return false;
    ServiceDescriptorProto o = (ServiceDescriptorProto) other;
    return equals(tagMap(), o.tagMap())
        && equals(name, o.name)
        && equals(method, o.method)
        && equals(doc, o.doc)
        && equals(options, o.options);
  }

  @Override
  public int hashCode() {
    int result = hashCode;
    if (result == 0) {
      result = tagMap().hashCode();
      result = result * 37 + (name != null ? name.hashCode() : 0);
      result = result * 37 + (method != null ? method.hashCode() : 1);
      result = result * 37 + (doc != null ? doc.hashCode() : 0);
      result = result * 37 + (options != null ? options.hashCode() : 0);
      hashCode = result;
    }
    return result;
  }

  public static final class Builder extends com.squareup.wire.Message.Builder<ServiceDescriptorProto, Builder> {
    public String name;

    public List<MethodDescriptorProto> method = Collections.emptyList();

    public String doc;

    public ServiceOptions options;

    public Builder() {
    }

    public Builder(ServiceDescriptorProto message) {
      super(message);
      if (message == null) return;
      this.name = message.name;
      this.method = copyOf(message.method);
      this.doc = message.doc;
      this.options = message.options;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder method(List<MethodDescriptorProto> method) {
      checkElementsNotNull(method);
      this.method = method;
      return this;
    }

    /**
     * Doc string for generated code.
     */
    public Builder doc(String doc) {
      this.doc = doc;
      return this;
    }

    public Builder options(ServiceOptions options) {
      this.options = options;
      return this;
    }

    @Override
    public ServiceDescriptorProto build() {
      return new ServiceDescriptorProto(name, method, doc, options, buildTagMap());
    }
  }
}
