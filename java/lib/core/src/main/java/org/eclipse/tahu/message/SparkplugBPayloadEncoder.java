/********************************************************************************
 * Copyright (c) 2014-2023 Cirrus Link Solutions and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Cirrus Link Solutions - initial implementation
 ********************************************************************************/

package org.eclipse.tahu.message;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.eclipse.tahu.message.model.DataSet;
import org.eclipse.tahu.message.model.DataSetDataType;
import org.eclipse.tahu.message.model.File;
import org.eclipse.tahu.message.model.MetaData;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.Parameter;
import org.eclipse.tahu.message.model.ParameterDataType;
import org.eclipse.tahu.message.model.PropertyDataType;
import org.eclipse.tahu.message.model.PropertySet;
import org.eclipse.tahu.message.model.PropertyValue;
import org.eclipse.tahu.message.model.Row;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.Template;
import org.eclipse.tahu.message.model.Value;
import org.eclipse.tahu.protobuf.SparkplugBProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

/**
 * A {@link PayloadDecode} implementation for encoding Sparkplug B payloads.
 */
public class SparkplugBPayloadEncoder implements PayloadEncoder<SparkplugBPayload> {

	private static final Logger logger = LoggerFactory.getLogger(SparkplugBPayloadEncoder.class.getName());

	/**
	 * Default Constructor
	 */
	public SparkplugBPayloadEncoder() {
		super();
	}

	@Override
	public byte[] getBytes(SparkplugBPayload payload, boolean stripDataTypes) throws IOException {

		SparkplugBProto.Payload.Builder protoMsg = SparkplugBProto.Payload.newBuilder();

		// Set the timestamp
		if (payload.getTimestamp() != null) {
			protoMsg.setTimestamp(payload.getTimestamp().getTime());
		}

		// Set the sequence number
		if (payload.getSeq() != null) {
			protoMsg.setSeq(payload.getSeq());
		}

		// Set the UUID if defined
		if (payload.getUuid() != null) {
			protoMsg.setUuid(payload.getUuid());
		}

		// Set the metrics
		for (Metric metric : payload.getMetrics()) {
			if (metric == null) {
				logger.warn("Not adding NULL metric");
				continue;
			}
			try {
				protoMsg.addMetrics(convertMetric(metric, stripDataTypes));
			} catch (Exception e) {
				logger.error("Failed to add metric: {}", metric.getName(), e);
				throw new RuntimeException(e);
			}
		}

		// Set the body
		if (payload.getBody() != null) {
			protoMsg.setBody(ByteString.copyFrom(payload.getBody()));
		}

		return protoMsg.build().toByteArray();
	}

	private SparkplugBProto.Payload.Metric.Builder convertMetric(Metric metric, boolean stripDataTypes)
			throws Exception {

		// build a metric
		SparkplugBProto.Payload.Metric.Builder builder = SparkplugBProto.Payload.Metric.newBuilder();

		// set the basic parameters
		if (!stripDataTypes) {
			builder.setDatatype(metric.getDataType().toIntValue());
		}
		builder = setMetricValue(builder, metric, stripDataTypes);

		// Set the name, data type, and value
		if (metric.hasName()) {
			builder.setName(metric.getName());
		} else {
			// name is an empty String by default and must be cleared
			builder.clearName();
		}

		// Set the alias
		if (metric.hasAlias()) {
			builder.setAlias(metric.getAlias());
		}

		// Set the timestamp
		if (metric.getTimestamp() != null) {
			builder.setTimestamp(metric.getTimestamp().getTime());
		}

		// Set isHistorical
		if (metric.getIsHistorical() != null) {
			builder.setIsHistorical(metric.isHistorical());
		}

		// Set isTransient
		if (metric.getIsTransient() != null) {
			builder.setIsTransient(metric.isTransient());
		}

		// Set isNull
		if (metric.getIsNull() != null) {
			builder.setIsNull(metric.isNull());
		}

		// Set the metadata
		if (metric.getMetaData() != null) {
			builder = setMetaData(builder, metric);
		}

		// Set the property set
		if (metric.getProperties() != null) {
			builder.setProperties(convertPropertySet(metric.getProperties()));
		}

		return builder;
	}

	private SparkplugBProto.Payload.Template.Parameter.Builder convertParameter(Parameter parameter) throws Exception {

		// build a metric
		SparkplugBProto.Payload.Template.Parameter.Builder builder =
				SparkplugBProto.Payload.Template.Parameter.newBuilder();

		if (logger.isTraceEnabled()) {
			logger.trace("Adding parameter: {}", parameter.getName());
			logger.trace("            type: {}", parameter.getType());
		}

		// Set the name
		builder.setName(parameter.getName());

		// Set the type and value
		builder = setParameterValue(builder, parameter);

		return builder;
	}

	private SparkplugBProto.Payload.PropertySet.Builder convertPropertySet(PropertySet propertySet) throws Exception {
		SparkplugBProto.Payload.PropertySet.Builder setBuilder = SparkplugBProto.Payload.PropertySet.newBuilder();

		Map<String, PropertyValue> map = propertySet.getPropertyMap();
		for (String key : map.keySet()) {
			SparkplugBProto.Payload.PropertyValue.Builder builder = SparkplugBProto.Payload.PropertyValue.newBuilder();
			PropertyValue value = map.get(key);
			PropertyDataType type = value.getType();
			builder.setType(type.toIntValue());
			if (value.getValue() == null) {
				builder.setIsNull(true);
			} else {
				switch (type) {
					case Boolean:
						builder.setBooleanValue((Boolean) value.getValue());
						break;
					case DateTime:
						builder.setLongValue(((Date) value.getValue()).getTime());
						break;
					case Double:
						builder.setDoubleValue((Double) value.getValue());
						break;
					case Float:
						builder.setFloatValue((Float) value.getValue());
						break;
					case Int8:
						builder.setIntValue((Byte) value.getValue());
						break;
					case Int16:
						builder.setIntValue((Short) value.getValue());
						break;
					case Int32:
						builder.setIntValue((Integer) value.getValue());
						break;
					case Int64:
						builder.setLongValue((Long) value.getValue());
						break;
					case UInt8:
						builder.setIntValue(Short.toUnsignedInt((Short) value.getValue()));
						break;
					case UInt16:
						builder.setIntValue((int) Integer.toUnsignedLong((Integer) value.getValue()));
						break;
					case UInt32:
						builder.setLongValue(Long.parseUnsignedLong(Long.toUnsignedString((Long) value.getValue())));
						break;
					case UInt64:
						builder.setLongValue(bigIntegerToUnsignedLong((BigInteger) value.getValue()));
						break;
					case String:
					case Text:
						builder.setStringValue((String) value.getValue());
						break;
					case PropertySet:
						builder.setPropertysetValue(convertPropertySet((PropertySet) value.getValue()));
						break;
					case PropertySetList:
						List<?> setList = (List<?>) value.getValue();
						SparkplugBProto.Payload.PropertySetList.Builder listBuilder =
								SparkplugBProto.Payload.PropertySetList.newBuilder();
						for (Object obj : setList) {
							listBuilder.addPropertyset(convertPropertySet((PropertySet) obj));
						}
						builder.setPropertysetsValue(listBuilder);
						break;
					case Unknown:
					default:
						logger.error("Unsupported PropertyDataType: '{}' for the '{}' property", value.getType(), key);
						throw new Exception("Failed to convert value " + value.getType());
				}
			}
			setBuilder.addKeys(key);
			setBuilder.addValues(builder);
		}
		return setBuilder;
	}

	private SparkplugBProto.Payload.Template.Parameter.Builder setParameterValue(
			SparkplugBProto.Payload.Template.Parameter.Builder builder, Parameter parameter) throws Exception {
		ParameterDataType type = parameter.getType();
		builder.setType(type.toIntValue());

		Object value = parameter.getValue();
		value = type == ParameterDataType.String && value == null ? "" : value;
		if (value != null) {
			switch (type) {
				case Boolean:
					builder.setBooleanValue(toBoolean(value));
					break;
				case DateTime:
					builder.setLongValue(((Date) value).getTime());
					break;
				case Double:
					builder.setDoubleValue((Double) value);
					break;
				case Float:
					builder.setFloatValue((Float) value);
					break;
				case Int8:
					builder.setIntValue((Byte) value);
					break;
				case Int16:
					builder.setIntValue((Short) value);
					break;
				case Int32:
					builder.setIntValue((Integer) value);
					break;
				case Int64:
					builder.setLongValue((Long) value);
					break;
				case UInt8:
					builder.setIntValue(Short.toUnsignedInt((Short) value));
					break;
				case UInt16:
					builder.setIntValue((int) Integer.toUnsignedLong((Integer) value));
					break;
				case UInt32:
					builder.setLongValue(Long.valueOf(Long.toUnsignedString(((BigInteger) value).longValue())));
					break;
				case UInt64:
					builder.setLongValue(bigIntegerToUnsignedLong((BigInteger) value));
					break;
				case Text:
				case String:
					builder.setStringValue((String) value);
					break;
				case Unknown:
				default:
					logger.error("Unknown Type: {}", type);
					throw new Exception("Failed to encode");

			}
		}
		return builder;
	}

	private SparkplugBProto.Payload.Metric.Builder setMetricValue(SparkplugBProto.Payload.Metric.Builder metricBuilder,
			Metric metric, boolean stripDataTypes) throws Exception {

		// Set the data type
		if (!stripDataTypes) {
			metricBuilder.setDatatype(metric.getDataType().toIntValue());
		}

		if (metric.getValue() == null) {
			metricBuilder.setIsNull(true);
		} else {
			switch (metric.getDataType()) {
				case Boolean:
					metricBuilder.setBooleanValue(toBoolean(metric.getValue()));
					break;
				case DateTime:
					metricBuilder.setLongValue(((Date) metric.getValue()).getTime());
					break;
				case File:
					metricBuilder.setBytesValue(ByteString.copyFrom(((File) metric.getValue()).getBytes()));
					SparkplugBProto.Payload.MetaData.Builder metaDataBuilder =
							SparkplugBProto.Payload.MetaData.newBuilder();
					metaDataBuilder.setFileName(((File) metric.getValue()).getFileName());
					metricBuilder.setMetadata(metaDataBuilder);
					break;
				case Float:
					metricBuilder.setFloatValue((Float) metric.getValue());
					break;
				case Double:
					metricBuilder.setDoubleValue((Double) metric.getValue());
					break;
				case Int8:
					metricBuilder.setIntValue((Byte) metric.getValue());
					break;
				case Int16:
					metricBuilder.setIntValue((Short) metric.getValue());
					break;
				case Int32:
					metricBuilder.setIntValue((Integer) metric.getValue());
					break;
				case Int64:
					metricBuilder.setLongValue((Long) metric.getValue());
					break;
				case UInt8:
					metricBuilder.setIntValue(Short.toUnsignedInt((Short) metric.getValue()));
					break;
				case UInt16:
					metricBuilder.setIntValue((int) Integer.toUnsignedLong((Integer) metric.getValue()));
					break;
				case UInt32:
					metricBuilder.setLongValue(Long.parseUnsignedLong(Long.toUnsignedString((Long) metric.getValue())));
					break;
				case UInt64:
					metricBuilder.setLongValue(bigIntegerToUnsignedLong((BigInteger) metric.getValue()));
					break;
				case String:
				case Text:
				case UUID:
					metricBuilder.setStringValue((String) metric.getValue());
					break;
				case Bytes:
					metricBuilder.setBytesValue(ByteString.copyFrom((byte[]) metric.getValue()));
					break;
				case DataSet:
					DataSet dataSet = (DataSet) metric.getValue();
					SparkplugBProto.Payload.DataSet.Builder dataSetBuilder =
							SparkplugBProto.Payload.DataSet.newBuilder();

					dataSetBuilder.setNumOfColumns(dataSet.getNumOfColumns());

					// Column names
					List<String> columnNames = dataSet.getColumnNames();
					if (columnNames != null && !columnNames.isEmpty()) {
						for (String name : columnNames) {
							// Add the column name
							dataSetBuilder.addColumns(name);
						}
					}

					// Column types
					List<DataSetDataType> columnTypes = dataSet.getTypes();
					if (columnTypes != null && !columnTypes.isEmpty()) {
						for (DataSetDataType type : columnTypes) {
							// Add the column type
							dataSetBuilder.addTypes(type.toIntValue());
						}
					}

					// Dataset rows
					List<Row> rows = dataSet.getRows();
					if (rows != null && !rows.isEmpty()) {
						for (Row row : rows) {
							SparkplugBProto.Payload.DataSet.Row.Builder protoRowBuilder =
									SparkplugBProto.Payload.DataSet.Row.newBuilder();
							List<Value<?>> values = row.getValues();
							if (values != null && !values.isEmpty()) {
								for (Value<?> value : values) {
									// Add the converted element
									protoRowBuilder.addElements(convertDataSetValue(value));
								}

								dataSetBuilder.addRows(protoRowBuilder);
							}
						}
					}

					// Finally add the dataset
					metricBuilder.setDatasetValue(dataSetBuilder);
					break;
				case Template:
					Template template = (Template) metric.getValue();
					SparkplugBProto.Payload.Template.Builder templateBuilder =
							SparkplugBProto.Payload.Template.newBuilder();

					// Set isDefinition
					templateBuilder.setIsDefinition(template.isDefinition());

					// Set Version
					if (template.getVersion() != null) {
						templateBuilder.setVersion(template.getVersion());
					}

					// Set Template Reference
					if (template.getTemplateRef() != null) {
						templateBuilder.setTemplateRef(template.getTemplateRef());
					}

					// Set the template metrics
					if (template.getMetrics() != null) {
						for (Metric templateMetric : template.getMetrics()) {
							templateBuilder.addMetrics(convertMetric(templateMetric, stripDataTypes));
						}
					}

					// Set the template parameters
					if (template.getParameters() != null) {
						for (Parameter parameter : template.getParameters()) {
							templateBuilder.addParameters(convertParameter(parameter));
						}
					}

					// Add the template to the metric
					metricBuilder.setTemplateValue(templateBuilder);
					break;
				case Int8Array:
					Byte[] int8ArrayValue = (Byte[]) metric.getValue();
					ByteBuffer int8ByteBuffer =
							ByteBuffer.allocate(int8ArrayValue.length).order(ByteOrder.LITTLE_ENDIAN);
					boolean hasNullInt8ArrayElements = false;
					for (Byte value : int8ArrayValue) {
						if (value != null) {
							int8ByteBuffer.put(value);
						} else {
							hasNullInt8ArrayElements = true;
							int8ByteBuffer.put((byte) 0);
						}
					}
					if (hasNullInt8ArrayElements) {
						logger.warn(
								"SparkplugB doesn't support 'null' elements in the {} Int8Array. All such elements will be set to 0.",
								metric.getName());
					}
					if (int8ByteBuffer.hasArray()) {
						metricBuilder.setBytesValue(ByteString.copyFrom(int8ByteBuffer.array()));
					}
					break;
				case Int16Array:
					Short[] int16ArrayValue = (Short[]) metric.getValue();
					ByteBuffer int16ByteBuffer =
							ByteBuffer.allocate(int16ArrayValue.length * 2).order(ByteOrder.LITTLE_ENDIAN);
					boolean hasNullInt16ArrayElements = false;
					for (Short value : int16ArrayValue) {
						if (value != null) {
							int16ByteBuffer.putShort(value);
						} else {
							hasNullInt16ArrayElements = true;
							int16ByteBuffer.putShort((short) 0);
						}
					}
					if (hasNullInt16ArrayElements) {
						logger.warn(
								"SparkplugB doesn't support 'null' elements in the {} Int16Array. All such elements will be set to 0.",
								metric.getName());
					}
					if (int16ByteBuffer.hasArray()) {
						metricBuilder.setBytesValue(ByteString.copyFrom(int16ByteBuffer.array()));
					}
					break;
				case Int32Array:
					Integer[] int32ArrayValue = (Integer[]) metric.getValue();
					ByteBuffer int32ByteBuffer =
							ByteBuffer.allocate(int32ArrayValue.length * 4).order(ByteOrder.LITTLE_ENDIAN);
					boolean hasNullInt32ArrayElements = false;
					for (Integer value : int32ArrayValue) {
						if (value != null) {
							int32ByteBuffer.putInt(value);
						} else {
							hasNullInt32ArrayElements = true;
							int32ByteBuffer.putInt(0);
						}
					}
					if (hasNullInt32ArrayElements) {
						logger.warn(
								"SparkplugB doesn't support 'null' elements in the {} Int32Array. All such elements will be set to 0.",
								metric.getName());
					}
					if (int32ByteBuffer.hasArray()) {
						metricBuilder.setBytesValue(ByteString.copyFrom(int32ByteBuffer.array()));
					}
					break;
				case Int64Array:
					Long[] int64ArrayValue = (Long[]) metric.getValue();
					ByteBuffer int64ByteBuffer =
							ByteBuffer.allocate(int64ArrayValue.length * 8).order(ByteOrder.LITTLE_ENDIAN);
					boolean hasNullInt64ArrayElements = false;
					for (Long value : int64ArrayValue) {
						if (value != null) {
							int64ByteBuffer.putLong(value);
						} else {
							hasNullInt64ArrayElements = true;
							int64ByteBuffer.putLong(0L);
						}
					}
					if (hasNullInt64ArrayElements) {
						logger.warn(
								"SparkplugB doesn't support 'null' elements in the {} Int64Array. All such elements will be set to 0.",
								metric.getName());
					}
					if (int64ByteBuffer.hasArray()) {
						metricBuilder.setBytesValue(ByteString.copyFrom(int64ByteBuffer.array()));
					}
					break;
				case UInt8Array:
					Short[] uInt8ArrayValue = (Short[]) metric.getValue();
					ByteBuffer uInt8ByteBuffer =
							ByteBuffer.allocate(uInt8ArrayValue.length).order(ByteOrder.LITTLE_ENDIAN);
					boolean hasNullUnt8ArrayElements = false;
					for (Short value : uInt8ArrayValue) {
						if (value != null) {
							uInt8ByteBuffer.put((byte) (value & 0xffff));
						} else {
							hasNullUnt8ArrayElements = true;
							uInt8ByteBuffer.put((byte) 0);
						}
					}
					if (hasNullUnt8ArrayElements) {
						logger.warn(
								"SparkplugB doesn't support 'null' elements in the {} UInt8Array. All such elements will be set to 0.",
								metric.getName());
					}
					if (uInt8ByteBuffer.hasArray()) {
						metricBuilder.setBytesValue(ByteString.copyFrom(uInt8ByteBuffer.array()));
					}
					break;
				case UInt16Array:
					Integer[] uInt16ArrayValue = (Integer[]) metric.getValue();
					ByteBuffer uInt16ByteBuffer =
							ByteBuffer.allocate(uInt16ArrayValue.length * 2).order(ByteOrder.LITTLE_ENDIAN);
					boolean hasNullUnt16ArrayElements = false;
					for (Integer value : uInt16ArrayValue) {
						if (value != null) {
							uInt16ByteBuffer.putShort((short) (value & 0xffffffff));
						} else {
							hasNullUnt16ArrayElements = true;
							uInt16ByteBuffer.putShort((short) 0);
						}
					}
					if (hasNullUnt16ArrayElements) {
						logger.warn(
								"SparkplugB doesn't support 'null' elements in the {} UInt16Array. All such elements will be set to 0.",
								metric.getName());
					}
					if (uInt16ByteBuffer.hasArray()) {
						metricBuilder.setBytesValue(ByteString.copyFrom(uInt16ByteBuffer.array()));
					}
					break;
				case UInt32Array:
					Long[] uInt32ArrayValue = (Long[]) metric.getValue();
					ByteBuffer uInt32ByteBuffer =
							ByteBuffer.allocate(uInt32ArrayValue.length * 4).order(ByteOrder.LITTLE_ENDIAN);
					boolean hasNullUnt32ArrayElements = false;
					for (Long value : uInt32ArrayValue) {
						if (value != null) {
							uInt32ByteBuffer.putInt((int) (value & 0xffffffffffffffffL));
						} else {
							hasNullUnt32ArrayElements = true;
							uInt32ByteBuffer.putInt(0);
						}
					}
					if (hasNullUnt32ArrayElements) {
						logger.warn(
								"SparkplugB doesn't support 'null' elements in the {} UInt32Array. All such elements will be set to 0.",
								metric.getName());
					}
					if (uInt32ByteBuffer.hasArray()) {
						metricBuilder.setBytesValue(ByteString.copyFrom(uInt32ByteBuffer.array()));
					}
					break;
				case UInt64Array:
					BigInteger[] uInt64ArrayValue = (BigInteger[]) metric.getValue();
					ByteBuffer uInt64ByteBuffer =
							ByteBuffer.allocate(uInt64ArrayValue.length * 8).order(ByteOrder.LITTLE_ENDIAN);
					boolean hasNullUnt64ArrayElements = false;
					for (BigInteger value : uInt64ArrayValue) {
						if (value != null) {
							uInt64ByteBuffer.putLong(bigIntegerToUnsignedLong(value));
						} else {
							hasNullUnt64ArrayElements = true;
							uInt64ByteBuffer.putLong(0L);
						}
					}
					if (hasNullUnt64ArrayElements) {
						logger.warn(
								"SparkplugB doesn't support 'null' elements in the {} UInt64Array. All such elements will be set to 0.",
								metric.getName());
					}
					if (uInt64ByteBuffer.hasArray()) {
						metricBuilder.setBytesValue(ByteString.copyFrom(uInt64ByteBuffer.array()));
					}
					break;
				case FloatArray:
					Float[] floatArrayValue = (Float[]) metric.getValue();
					ByteBuffer floatByteBuffer =
							ByteBuffer.allocate(floatArrayValue.length * 4).order(ByteOrder.LITTLE_ENDIAN);
					boolean hasNullFloatArrayElements = false;
					for (Float value : floatArrayValue) {
						if (value != null) {
							floatByteBuffer.putFloat(value);
						} else {
							hasNullFloatArrayElements = true;
							floatByteBuffer.putFloat(0);
						}
					}
					if (hasNullFloatArrayElements) {
						logger.warn(
								"SparkplugB doesn't support 'null' elements in the {} FloatArray. All such elements will be set to 0.",
								metric.getName());
					}
					if (floatByteBuffer.hasArray()) {
						metricBuilder.setBytesValue(ByteString.copyFrom(floatByteBuffer.array()));
					}
					break;
				case DoubleArray:
					Double[] doubleArrayValue = (Double[]) metric.getValue();
					ByteBuffer doubleByteBuffer =
							ByteBuffer.allocate(doubleArrayValue.length * 8).order(ByteOrder.LITTLE_ENDIAN);
					boolean hasNullDoubleArrayElements = false;
					for (Double value : doubleArrayValue) {
						if (value != null) {
							doubleByteBuffer.putDouble(value);
						} else {
							hasNullDoubleArrayElements = true;
							doubleByteBuffer.putDouble(0);
						}
					}
					if (hasNullDoubleArrayElements) {
						logger.warn(
								"SparkplugB doesn't support 'null' elements in the {} DoubleArray. All such elements will be set to 0.",
								metric.getName());
					}
					if (doubleByteBuffer.hasArray()) {
						metricBuilder.setBytesValue(ByteString.copyFrom(doubleByteBuffer.array()));
					}
					break;
				case BooleanArray:
					Boolean[] booleanArrayValue = (Boolean[]) metric.getValue();
					int numberOfBytes = (int) Math.ceil((double) booleanArrayValue.length / 8);
					ByteBuffer booleanByteBuffer =
							ByteBuffer.allocate(4 + numberOfBytes).order(ByteOrder.LITTLE_ENDIAN);

					// The first 4 bytes is the number of booleans in the array
					booleanByteBuffer.putInt(booleanArrayValue.length);

					// Get the remaining bytes
					boolean hasNullBooleanArrayElements = false;
					for (int i = 0; i < numberOfBytes; i++) {
						byte nextByte = 0;
						for (int bit = 0; bit < 8; bit++) {
							int index = i * 8 + bit;
							if (index < booleanArrayValue.length) {
								Boolean value = booleanArrayValue[index];
								if (value == null) {
									hasNullBooleanArrayElements = true;
									value = Boolean.valueOf(false);
								}
								if (value.booleanValue()) {
									nextByte |= (128 >> bit);
								}
							}
						}
						booleanByteBuffer.put(nextByte);
					}
					if (hasNullBooleanArrayElements) {
						logger.warn(
								"SparkplugB doesn't support 'null' elements in the {} BooleanArray. All such elements will be set to 'false'.",
								metric.getName());
					}
					metricBuilder.setBytesValue(ByteString.copyFrom(booleanByteBuffer.array()));
					break;
				case StringArray:
					String[] stringArrayValue = (String[]) metric.getValue();

					int size = 0;
					List<byte[]> bytesArrays = new ArrayList<>();
					boolean hasNullStringArrayElements = false;
					for (String string : stringArrayValue) {
						byte[] stringBytes = null;
						if (string != null) {
							stringBytes = string.getBytes(StandardCharsets.UTF_8);
						} else {
							hasNullStringArrayElements = true;
							stringBytes = new byte[0];
						}
						size = size + stringBytes.length + 1;
						bytesArrays.add(stringBytes);
					}
					if (hasNullStringArrayElements) {
						logger.warn(
								"SparkplugB doesn't support 'null' elements in the {} StringArray. All such elements will be set to an empty string.",
								metric.getName());
					}
					ByteBuffer stringByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
					for (byte[] bytesArray : bytesArrays) {
						stringByteBuffer.put(bytesArray);
						stringByteBuffer.put((byte) 0);
					}
					if (stringByteBuffer.hasArray()) {
						metricBuilder.setBytesValue(ByteString.copyFrom(stringByteBuffer.array()));
					}
					break;
				case DateTimeArray:
					Date[] dateTimeArrayValue = (Date[]) metric.getValue();
					ByteBuffer dateTimeByteBuffer =
							ByteBuffer.allocate(dateTimeArrayValue.length * 8).order(ByteOrder.LITTLE_ENDIAN);
					boolean hasNullDateTimeArrayElements = false;
					for (Date value : dateTimeArrayValue) {
						if (value != null) {
							dateTimeByteBuffer.putLong(value.getTime());
						} else {
							hasNullDateTimeArrayElements = true;
							dateTimeByteBuffer.putLong(new Date(0L).getTime());
						}
					}
					if (hasNullDateTimeArrayElements) {
						logger.warn(
								"SparkplugB doesn't support 'null' elements in the {} DateTimeArray. All such elements will be set to start of epoch.",
								metric.getName());
					}
					if (dateTimeByteBuffer.hasArray()) {
						metricBuilder.setBytesValue(ByteString.copyFrom(dateTimeByteBuffer.array()));
					}
					break;
				case Unknown:
				default:
					logger.error("Unsupported MetricDataType: {} for the {} metric", metric.getDataType(),
							metric.getName());
					throw new Exception("Failed to encode");

			}
		}
		return metricBuilder;
	}

	private SparkplugBProto.Payload.Metric.Builder setMetaData(SparkplugBProto.Payload.Metric.Builder metricBuilder,
			Metric metric) throws Exception {
		// If the builder has been built already - use it
		SparkplugBProto.Payload.MetaData.Builder metaDataBuilder = metricBuilder.getMetadataBuilder() != null
				? metricBuilder.getMetadataBuilder()
				: SparkplugBProto.Payload.MetaData.newBuilder();

		MetaData metaData = metric.getMetaData();
		if (metaData.getContentType() != null) {
			metaDataBuilder.setContentType(metaData.getContentType());
		}
		if (metaData.getSize() != null) {
			metaDataBuilder.setSize(metaData.getSize());
		}
		if (metaData.getSeq() != null) {
			metaDataBuilder.setSeq(metaData.getSeq());
		}
		if (metaData.getFileName() != null) {
			metaDataBuilder.setFileName(metaData.getFileName());
		}
		if (metaData.getFileType() != null) {
			metaDataBuilder.setFileType(metaData.getFileType());
		}
		if (metaData.getMd5() != null) {
			metaDataBuilder.setMd5(metaData.getMd5());
		}
		if (metaData.isMultiPart() != null) {
			metaDataBuilder.setIsMultiPart(metaData.isMultiPart());
		}
		if (metaData.getDescription() != null) {
			metaDataBuilder.setDescription(metaData.getDescription());
		}
		metricBuilder.setMetadata(metaDataBuilder);

		return metricBuilder;
	}

	private SparkplugBProto.Payload.DataSet.DataSetValue.Builder convertDataSetValue(Value<?> value) throws Exception {
		SparkplugBProto.Payload.DataSet.DataSetValue.Builder protoValueBuilder =
				SparkplugBProto.Payload.DataSet.DataSetValue.newBuilder();

		// Set the value
		DataSetDataType type = value.getType();

		switch (type) {
			case Int8:
				if (value == null || value.getValue() == null) {
					return protoValueBuilder;
				}
				protoValueBuilder.setIntValue((Byte) value.getValue());
				break;
			case Int16:
				if (value == null || value.getValue() == null) {
					return protoValueBuilder;
				}
				protoValueBuilder.setIntValue((Short) value.getValue());
				break;
			case Int32:
				if (value == null || value.getValue() == null) {
					return protoValueBuilder;
				}
				protoValueBuilder.setIntValue((Integer) value.getValue());
				break;
			case Int64:
				if (value == null || value.getValue() == null) {
					return protoValueBuilder;
				}
				protoValueBuilder.setLongValue((Long) value.getValue());
				break;
			case UInt8:
				if (value == null || value.getValue() == null) {
					return protoValueBuilder;
				}
				protoValueBuilder.setIntValue(Short.toUnsignedInt((Short) value.getValue()));
				break;
			case UInt16:
				if (value == null || value.getValue() == null) {
					return protoValueBuilder;
				}
				protoValueBuilder.setIntValue((int) Integer.toUnsignedLong((Integer) value.getValue()));
				break;
			case UInt32:
				if (value == null || value.getValue() == null) {
					return protoValueBuilder;
				}
				protoValueBuilder.setLongValue(Long.parseUnsignedLong(Long.toUnsignedString((Long) value.getValue())));
				break;
			case UInt64:
				if (value == null || value.getValue() == null) {
					return protoValueBuilder;
				}
				protoValueBuilder.setLongValue(bigIntegerToUnsignedLong((BigInteger) value.getValue()));
				break;
			case Float:
				if (value == null || value.getValue() == null) {
					return protoValueBuilder;
				}
				protoValueBuilder.setFloatValue((Float) value.getValue());
				break;
			case Double:
				if (value == null || value.getValue() == null) {
					return protoValueBuilder;
				}
				protoValueBuilder.setDoubleValue((Double) value.getValue());
				break;
			case String:
			case Text:
				if (value == null || value.getValue() == null) {
					return protoValueBuilder;
				}
				protoValueBuilder.setStringValue((String) value.getValue());
				break;
			case Boolean:
				if (value == null || value.getValue() == null) {
					return protoValueBuilder;
				}
				protoValueBuilder.setBooleanValue(toBoolean(value.getValue()));
				break;
			case DateTime:
				if (value == null || value.getValue() == null) {
					return protoValueBuilder;
				}
				protoValueBuilder.setLongValue(((Date) value.getValue()).getTime());
				break;
			default:
				logger.error("Unknown DataSetDataType DataType: " + value.getType());
				throw new Exception("Failed to convert value " + value.getType());
		}

		return protoValueBuilder;
	}

	private Boolean toBoolean(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Integer) {
			return ((Integer) value).intValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
		} else if (value instanceof Long) {
			return ((Long) value).longValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
		} else if (value instanceof Float) {
			return ((Float) value).floatValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
		} else if (value instanceof Double) {
			return ((Double) value).doubleValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
		} else if (value instanceof Short) {
			return ((Short) value).shortValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
		} else if (value instanceof Byte) {
			return ((Byte) value).byteValue() == 0 ? Boolean.FALSE : Boolean.TRUE;
		} else if (value instanceof String) {
			return Boolean.parseBoolean(value.toString());
		}
		return (Boolean) value;
	}

	private long bigIntegerToUnsignedLong(BigInteger bigInteger) {
		BigInteger bref = BigInteger.ONE.shiftLeft(64);
		if (bigInteger.compareTo(BigInteger.ZERO) < 0)
			bigInteger = bigInteger.add(bref);
		if (bigInteger.compareTo(bref) >= 0 || bigInteger.compareTo(BigInteger.ZERO) < 0)
			throw new RuntimeException("Out of range: " + bigInteger);
		return bigInteger.longValue();
	}
}
