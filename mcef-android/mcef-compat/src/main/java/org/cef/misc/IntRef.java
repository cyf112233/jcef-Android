package org.cef.misc;

/** 上游 API 是 get()/set()；getValue()/setValue() 保留兼容。 */
public class IntRef {
    public int value;
    public IntRef(int value) { this.value = value; }
    public int get() { return value; }
    public void set(int v) { this.value = v; }
    public int getValue() { return value; }
    public void setValue(int v) { this.value = v; }
}
