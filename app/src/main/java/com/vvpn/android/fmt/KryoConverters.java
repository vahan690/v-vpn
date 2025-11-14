package com.vvpn.android.fmt;

import androidx.room.TypeConverter;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.vvpn.android.database.SubscriptionBean;
import com.vvpn.android.fmt.anytls.AnyTLSBean;
import com.vvpn.android.fmt.direct.DirectBean;
import com.vvpn.android.fmt.http.HttpBean;
import com.vvpn.android.fmt.hysteria.HysteriaBean;
import com.vvpn.android.fmt.internal.ChainBean;
import com.vvpn.android.fmt.internal.ProxySetBean;
import com.vvpn.android.fmt.juicity.JuicityBean;
import com.vvpn.android.fmt.mieru.MieruBean;
import com.vvpn.android.fmt.naive.NaiveBean;
import com.vvpn.android.fmt.shadowquic.ShadowQUICBean;
import com.vvpn.android.fmt.shadowsocks.ShadowsocksBean;
import com.vvpn.android.fmt.socks.SOCKSBean;
import com.vvpn.android.fmt.ssh.SSHBean;
import com.vvpn.android.fmt.trojan.TrojanBean;
import com.vvpn.android.fmt.tuic.TuicBean;
import com.vvpn.android.fmt.v2ray.VMessBean;
import com.vvpn.android.fmt.wireguard.WireGuardBean;
import com.vvpn.android.ktx.KryosKt;
import com.vvpn.android.ktx.Logs;
import com.vvpn.android.fmt.config.ConfigBean;
import com.vvpn.android.fmt.shadowtls.ShadowTLSBean;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

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
    public static SOCKSBean socksDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new SOCKSBean(), bytes);
    }

    @TypeConverter
    public static HttpBean httpDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new HttpBean(), bytes);
    }

    @TypeConverter
    public static ShadowsocksBean shadowsocksDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new ShadowsocksBean(), bytes);
    }

    @TypeConverter
    public static ConfigBean configDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new ConfigBean(), bytes);
    }

    @TypeConverter
    public static VMessBean vmessDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new VMessBean(), bytes);
    }

    @TypeConverter
    public static TrojanBean trojanDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new TrojanBean(), bytes);
    }

    @TypeConverter
    public static MieruBean mieruDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new MieruBean(), bytes);
    }

    @TypeConverter
    public static NaiveBean naiveDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new NaiveBean(), bytes);
    }

    @TypeConverter
    public static HysteriaBean hysteriaDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new HysteriaBean(), bytes);
    }

    @TypeConverter
    public static SSHBean sshDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new SSHBean(), bytes);
    }

    @TypeConverter
    public static WireGuardBean wireguardDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new WireGuardBean(), bytes);
    }

    @TypeConverter
    public static TuicBean tuicDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new TuicBean(), bytes);
    }

    @TypeConverter
    public static JuicityBean juicityDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new JuicityBean(), bytes);
    }

    @TypeConverter
    public static DirectBean directDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new DirectBean(), bytes);
    }

    @TypeConverter
    public static AnyTLSBean anyTLSDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new AnyTLSBean(), bytes);
    }

    @TypeConverter
    public static ShadowTLSBean shadowTLSDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new ShadowTLSBean(), bytes);
    }

    @TypeConverter
    public static ShadowQUICBean shadowQUICDeserialize(byte[] bytes) {
        if (isEmpty(bytes)) return null;
        return deserialize(new ShadowQUICBean(), bytes);
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
