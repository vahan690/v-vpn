package io.vvpn.android.fmt;

import androidx.room.TypeConverter;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import io.vvpn.android.database.SubscriptionBean;
import io.vvpn.android.fmt.anytls.AnyTLSBean;
import io.vvpn.android.fmt.direct.DirectBean;
import io.vvpn.android.fmt.http.HttpBean;
import io.vvpn.android.fmt.hysteria.HysteriaBean;
import io.vvpn.android.fmt.internal.ChainBean;
import io.vvpn.android.fmt.internal.ProxySetBean;
import io.vvpn.android.fmt.juicity.JuicityBean;
import io.vvpn.android.fmt.mieru.MieruBean;
import io.vvpn.android.fmt.naive.NaiveBean;
import io.vvpn.android.fmt.shadowquic.ShadowQUICBean;
import io.vvpn.android.fmt.shadowsocks.ShadowsocksBean;
import io.vvpn.android.fmt.socks.SOCKSBean;
import io.vvpn.android.fmt.ssh.SSHBean;
import io.vvpn.android.fmt.trojan.TrojanBean;
import io.vvpn.android.fmt.tuic.TuicBean;
import io.vvpn.android.fmt.v2ray.VMessBean;
import io.vvpn.android.fmt.wireguard.WireGuardBean;
import io.vvpn.android.ktx.KryosKt;
import io.vvpn.android.ktx.Logs;
import io.vvpn.android.fmt.config.ConfigBean;
import io.vvpn.android.fmt.shadowtls.ShadowTLSBean;

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
