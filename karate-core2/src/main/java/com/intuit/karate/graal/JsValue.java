/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.graal;

import com.intuit.karate.FileUtils;
import com.intuit.karate.data.JsonUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.graalvm.polyglot.Value;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class JsValue {

    public static enum Type {
        OBJECT,
        ARRAY,
        FUNCTION,
        OTHER
    }

    private final Value original;
    private final Object value;
    public final Type type;

    public JsValue(Value v) {
        this.original = v;
        if (v.isProxyObject()) {
            Object o = v.asProxyObject();
            if (o instanceof JsXml) {
                value = ((JsXml) o).getNode();
                type = Type.OBJECT;
            } else if (o instanceof JsMap) {
                value = ((JsMap) o).getMap();
                type = Type.OBJECT;
            } else if (o instanceof JsList) {
                value = ((JsList) o).getList();
                type = Type.ARRAY;
            } else { // e.g. custom bridge, e.g. Request
                value = v.as(Object.class);
                type = Type.OTHER;
            }
        } else if (v.isHostObject()) { // pojo
            value = v.asHostObject();
            type = Type.OTHER;
        } else if (v.canExecute()) {
            value = v.as(Function.class);
            type = Type.FUNCTION;
        } else if (v.hasArrayElements()) {
            int size = (int) v.getArraySize();
            List list = new ArrayList(size);
            for (int i = 0; i < size; i++) {
                Value child = v.getArrayElement(i);
                list.add(new JsValue(child).value);
            }
            value = list;
            type = Type.ARRAY;
        } else if (v.hasMembers()) {
            Set<String> keys = v.getMemberKeys();
            Map<String, Object> map = new LinkedHashMap(keys.size());
            for (String key : keys) {
                Value child = v.getMember(key);
                map.put(key, new JsValue(child).value);
            }
            value = map;
            type = Type.OBJECT;
        } else {
            value = v.as(Object.class);
            type = Type.OTHER;
        }
    }

    public Object getValue() {
        return value;
    }

    public Map<String, Object> getAsMap() {
        return (Map) value;
    }

    public List getAsList() {
        return (List) value;
    }

    public Value getOriginal() {
        return original;
    }

    public JsValue invoke(Object... args) {
        return new JsValue(original.execute(args));
    }

    public boolean isObject() {
        return type == Type.OBJECT;
    }

    public boolean isArray() {
        return type == Type.ARRAY;
    }

    public boolean isTrue() {
        if (type != Type.OTHER || !Boolean.class.equals(value.getClass())) {
            return false;
        }
        return (Boolean) value;
    }

    public boolean isFunction() {
        return type == Type.FUNCTION;
    }

    public boolean isOther() {
        return type == Type.OTHER;
    }

    @Override
    public String toString() {
        return original.toString();
    }

    public static Object fromJava(Object o) {
        if (o instanceof Function) { // can be map also, so do this first
            return o;
        } else if (o instanceof List) {
            return new JsList((List) o);
        } else if (o instanceof Map) {
            return new JsMap((Map) o);
        } else if (o instanceof Node) {
            return new JsXml((Node) o);
        } else {
            return o;
        }
    }

    public static Object toJava(Value v) {
        return new JsValue(v).getValue();
    }

    public static byte[] toBytes(Value v) {
        return toBytes(toJava(v));
    }

    public static byte[] toBytes(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Map || o instanceof List) {
            return FileUtils.toBytes(JsonUtils.toJson(o));
        } else if (o instanceof byte[]) {
            return (byte[]) o;
        } else {
            return FileUtils.toBytes(o.toString());
        }
    }

    public static Object fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        String raw = FileUtils.toString(bytes);
        return fromString(raw);
    }

    public static Object fromString(String raw) {
        char firstChar = raw.trim().charAt(0);
        if (firstChar == '{' || firstChar == '[') {
            Object o = JsonUtils.fromJson(raw);
            return JsValue.fromJava(o);
        } else {
            return raw;
        }
    }

}
