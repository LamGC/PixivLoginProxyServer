package net.lamgc.plps;

import com.github.monkeywie.proxyee.crt.CertUtil;
import com.github.monkeywie.proxyee.server.HttpProxyCACertFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Objects;

/**
 * 通过InputStream导入CA证书及私钥
 */
public class InputStreamCertFactory implements HttpProxyCACertFactory {

    private final X509Certificate caCert;

    private final PrivateKey caPrivateKey;

    /**
     * 构造CaFactory并加载指定的CA证书及密钥
     * @param caCertInput CA证书输入流，证书需以X.509格式编码
     * @param caPriKeyInput CA私钥输入流，私钥需以PKCS#8格式编码
     * @throws IOException 当方法尝试读取输入流发生异常时抛出
     * @throws CertificateException 当证书异常时抛出异常
     */
    public InputStreamCertFactory(InputStream caCertInput, InputStream caPriKeyInput) throws IOException, CertificateException {
        this.caCert = CertUtil.loadCert(Objects.requireNonNull(caCertInput));
        try {
            this.caPrivateKey = CertUtil.loadPriKey(Objects.requireNonNull(caPriKeyInput));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }


    @Override
    public X509Certificate getCACert() {
        return caCert;
    }

    @Override
    public PrivateKey getCAPriKey() {
        return caPrivateKey;
    }
}
