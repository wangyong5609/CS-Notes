版本信息

> Axis2：1.5.1

> Java: 8

最近工作中需要将一段 SOAP 请求代码升级，将老的 NTLM 认证改为 WSS 认证。

## NTLM 认证

```java
HttpTransportProperties.Authenticator auth = new HttpTransportProperties.Authenticator();

PaymetricXiPayStub stub = new PaymetricXiPayStub(getEndpointUrl(salesOrg));
auth.setUsername(getUsername(salesOrg));
auth.setPassword(getPassword(salesOrg));
auth.setDomain(getDomain(salesOrg));
auth.setHost(getHost(salesOrg));
// For SSL
auth.setPort(443);
// For Http
// auth.setPort(80);

Options options = stub._getServiceClient().getOptions();
options.setProperty(HTTPConstants.AUTHENTICATE, auth);
// If using http, comment out the following line of code
options.setProperty(HTTPConstants.CHUNKED, Boolean.FALSE);
stub._getServiceClient().setOptions(options);
```



## WSS 认证

添加 WSS 认证实际是在 SOAP 请求的 Header 中配置如下 XML：

```xml
<soapenv:Header>
    <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
        <wsse:UsernameToken>
            <wsse:Username>test</wsse:Username>
            <wsse:Password>test</wsse:Password>
        </wsse:UsernameToken>
    </wsse:Security>
</soapenv:Header>
```



使用代码实现如下：

```java
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPHeaderBlock;

// 省略代码
public static final String LOCAL_NAME = "Security";
public static final String USERNAME_TOKEN = "UsernameToken";
public static final String USERNAME = "Username";
public static final String PASSWORD = "Password";
public static final String NAMESPACE_URI = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
public static final String NAMESPACE_PREFIX = "wsse";

// 省略代码
PaymetricXiPayStub stub = new PaymetricXiPayStub(getEndpointUrl(salesOrg));
OMFactory fac = OMAbstractFactory.getOMFactory();
SOAPFactory factory = OMAbstractFactory.getSOAP12Factory();
OMNamespace nsWSSE = fac.createOMNamespace(NAMESPACE_URI, NAMESPACE_PREFIX);
SOAPHeaderBlock header = factory.createSOAPHeaderBlock(LOCAL_NAME, nsWSSE);
OMElement usernameToken = fac.createOMElement(USERNAME_TOKEN, nsWSSE);
OMElement username = fac.createOMElement(USERNAME, nsWSSE);
username.setText(getUsername(salesOrg));
usernameToken.addChild(username);
OMElement password = fac.createOMElement(PASSWORD, nsWSSE);
password.setText(getPassword(salesOrg));
usernameToken.addChild(password);
header.addChild(usernameToken);
stub._getServiceClient().addHeader(header);
```



以上代码主要是向 org.apache.axis2.client.ServiceClient 添加 `UsernameToken`Header。

## 处理response抛出异常

SOAP 请求响应状态为200，但是程序抛出异常信息：Must Understand check failed for header Security。

因为 response 的 Header 中有 `mustUnderstand`标识

![image-20241018140911049](/Users/mac/IdeaProjects/CS-Notes/Java/添加WSSHeader到Axis2-web-service.assets/image-20241018140911049.png)

这个标识意味着我们必须手动去处理这个Header，将`mustUnderstand`设为 `false`，或者将`processed`设为`true`都可以，如下代码：

```java
// _messageContext 是 org.apache.axis2.context.MessageContext
AxisConfiguration axisConfiguration = _messageContext.getConfigurationContext().getAxisConfiguration();
List<Phase> phases = axisConfiguration.getOutFlowPhases();
Phase phase = new Phase();
phase.addHandler(new MustUnderstandHandler());
phases.add(phase);
axisConfiguration.setInPhasesUptoAndIncludingPostDispatch(phases);
// 省略代码
static class MustUnderstandHandler extends AbstractHandler {
    @Override
    public InvocationResponse invoke(MessageContext messageContext) throws AxisFault {
        SOAPEnvelope env = messageContext.getEnvelope();
        SOAPHeader header = env.getHeader();
        if (header != null) {
            for (Iterator<?> itr = header.getChildElements(); itr.hasNext(); ) {
                SOAPHeaderBlock headerBlock = (SOAPHeaderBlock) itr.next();
                if (LOCAL_NAME.equals(headerBlock.getLocalName()) && headerBlock.getMustUnderstand()) {
                    headerBlock.setMustUnderstand(false);
                }
            }
        }
        return InvocationResponse.CONTINUE;
    }
}
```



## 参考链接

- https://stackoverflow.com/questions/24007251/how-to-add-security-header-to-apache-axis-web-service-request
- https://stackoverflow.com/questions/4715027/axis2-disable-mustunderstand-header-check