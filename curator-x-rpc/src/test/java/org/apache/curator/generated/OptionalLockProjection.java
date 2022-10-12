/**
 * Autogenerated by Thrift Compiler (0.9.1)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package org.apache.curator.generated;

import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;

import org.apache.thrift.scheme.TupleScheme;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.EncodingUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.server.AbstractNonblockingServer.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Collections;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptionalLockProjection implements org.apache.thrift.TBase<OptionalLockProjection, OptionalLockProjection._Fields>, java.io.Serializable, Cloneable, Comparable<OptionalLockProjection> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("OptionalLockProjection");

  private static final org.apache.thrift.protocol.TField LOCK_PROJECTION_FIELD_DESC = new org.apache.thrift.protocol.TField("lockProjection", org.apache.thrift.protocol.TType.STRUCT, (short)1);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new OptionalLockProjectionStandardSchemeFactory());
    schemes.put(TupleScheme.class, new OptionalLockProjectionTupleSchemeFactory());
  }

  public LockProjection lockProjection; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    LOCK_PROJECTION((short)1, "lockProjection");

    private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

    static {
      for (_Fields field : EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // LOCK_PROJECTION
          return LOCK_PROJECTION;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final String _fieldName;

    _Fields(short thriftId, String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.LOCK_PROJECTION, new org.apache.thrift.meta_data.FieldMetaData("lockProjection", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, LockProjection.class)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(OptionalLockProjection.class, metaDataMap);
  }

  public OptionalLockProjection() {
  }

  public OptionalLockProjection(
    LockProjection lockProjection)
  {
    this();
    this.lockProjection = lockProjection;
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public OptionalLockProjection(OptionalLockProjection other) {
    if (other.isSetLockProjection()) {
      this.lockProjection = new LockProjection(other.lockProjection);
    }
  }

  public OptionalLockProjection deepCopy() {
    return new OptionalLockProjection(this);
  }

  @Override
  public void clear() {
    this.lockProjection = null;
  }

  public LockProjection getLockProjection() {
    return this.lockProjection;
  }

  public OptionalLockProjection setLockProjection(LockProjection lockProjection) {
    this.lockProjection = lockProjection;
    return this;
  }

  public void unsetLockProjection() {
    this.lockProjection = null;
  }

  /** Returns true if field lockProjection is set (has been assigned a value) and false otherwise */
  public boolean isSetLockProjection() {
    return this.lockProjection != null;
  }

  public void setLockProjectionIsSet(boolean value) {
    if (!value) {
      this.lockProjection = null;
    }
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
    case LOCK_PROJECTION:
      if (value == null) {
        unsetLockProjection();
      } else {
        setLockProjection((LockProjection)value);
      }
      break;

    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
    case LOCK_PROJECTION:
      return getLockProjection();

    }
    throw new IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
    case LOCK_PROJECTION:
      return isSetLockProjection();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof OptionalLockProjection)
      return this.equals((OptionalLockProjection)that);
    return false;
  }

  public boolean equals(OptionalLockProjection that) {
    if (that == null)
      return false;

    boolean this_present_lockProjection = true && this.isSetLockProjection();
    boolean that_present_lockProjection = true && that.isSetLockProjection();
    if (this_present_lockProjection || that_present_lockProjection) {
      if (!(this_present_lockProjection && that_present_lockProjection))
        return false;
      if (!this.lockProjection.equals(that.lockProjection))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public int compareTo(OptionalLockProjection other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = Boolean.valueOf(isSetLockProjection()).compareTo(other.isSetLockProjection());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetLockProjection()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.lockProjection, other.lockProjection);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("OptionalLockProjection(");
    boolean first = true;

    sb.append("lockProjection:");
    if (this.lockProjection == null) {
      sb.append("null");
    } else {
      sb.append(this.lockProjection);
    }
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
    if (lockProjection != null) {
      lockProjection.validate();
    }
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
    try {
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class OptionalLockProjectionStandardSchemeFactory implements SchemeFactory {
    public OptionalLockProjectionStandardScheme getScheme() {
      return new OptionalLockProjectionStandardScheme();
    }
  }

  private static class OptionalLockProjectionStandardScheme extends StandardScheme<OptionalLockProjection> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, OptionalLockProjection struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // LOCK_PROJECTION
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.lockProjection = new LockProjection();
              struct.lockProjection.read(iprot);
              struct.setLockProjectionIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, OptionalLockProjection struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.lockProjection != null) {
        oprot.writeFieldBegin(LOCK_PROJECTION_FIELD_DESC);
        struct.lockProjection.write(oprot);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class OptionalLockProjectionTupleSchemeFactory implements SchemeFactory {
    public OptionalLockProjectionTupleScheme getScheme() {
      return new OptionalLockProjectionTupleScheme();
    }
  }

  private static class OptionalLockProjectionTupleScheme extends TupleScheme<OptionalLockProjection> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, OptionalLockProjection struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetLockProjection()) {
        optionals.set(0);
      }
      oprot.writeBitSet(optionals, 1);
      if (struct.isSetLockProjection()) {
        struct.lockProjection.write(oprot);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, OptionalLockProjection struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(1);
      if (incoming.get(0)) {
        struct.lockProjection = new LockProjection();
        struct.lockProjection.read(iprot);
        struct.setLockProjectionIsSet(true);
      }
    }
  }

}

