package com.vvpn.android.fmt;

import androidx.room.TypeConverter;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.vvpn.android.database.SubscriptionBean;
import com.vvpn.android.fmt.direct.DirectBean;
import com.vvpn.android.fmt.hysteria.HysteriaBean;
import com.vvpn.android.fmt.internal.ChainBean;
import com.vvpn.android.fmt.internal.ProxySetBean;
import com.vvpn.android.ktx.KryosKt;
import com.vvpn.android.ktx.Logs;
import com.vvpn.android.fmt.config.ConfigBean;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Kryo converters for V-VPN - Hysteria2 only protocol support
 */
public class KryoConverters {

    private static final byte[] NULL = new byte[0];

    @TypeConverter
    public static byte[] serialize(Serializable bean) {
        if (bean == null) return NULL;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBufferOutput buffer = KryosKt.byteBuffer(out);
        bean.serializeToBuffer(buffer);
        buffer.flush();
        buffer.close();
        return out.toByteArray();
    }

    public static <T extends Serializable> T deserialize(T bean, byte[] bytes) {
        if (bytes == null) return bean;
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        ByteBufferInput buffer = KryosKt.byteBuffer(input);
        try {
            bean.deserializeFromBuffer(buffer);
        } catch (KryoException e) {
            Logs.INSTANCE.w(e);
        }
        bean.initializeDefaultValues();
        return bean;
    }

    private static Boolean isEmpty(byte[] bytes) {
        return bytes == null || bytes.length == 0;
    }

    @TypeConverter
    public static ConfigBean configDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new ConfigBean(), bytes);
    }

    @TypeConverter
    public static HysteriaBean hysteriaDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new HysteriaBean(), bytes);
    }

    @TypeConverter
    public static DirectBean directDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new DirectBean(), bytes);
    }

    @TypeConverter
    public static ProxySetBean proxySetDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new ProxySetBean(), bytes);
    }

    @TypeConverter
    public static ChainBean chainDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new ChainBean(), bytes);
    }

    @TypeConverter
    public static SubscriptionBean subscriptionDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new SubscriptionBean(), bytes);
    }
}
