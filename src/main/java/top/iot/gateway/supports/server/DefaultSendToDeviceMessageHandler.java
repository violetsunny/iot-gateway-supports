package top.iot.gateway.supports.server;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.iot.gateway.core.device.*;
import top.iot.gateway.core.enums.ErrorCode;
import top.iot.gateway.core.exception.DeviceOperationException;
import top.iot.gateway.core.message.*;
import top.iot.gateway.core.message.codec.EncodedMessage;
import top.iot.gateway.core.message.codec.ToDeviceMessageContext;
import top.iot.gateway.core.server.MessageHandler;
import top.iot.gateway.core.server.session.ChildrenDeviceSession;
import top.iot.gateway.core.server.session.DeviceSession;
import top.iot.gateway.core.server.session.DeviceSessionManager;
import top.iot.gateway.core.trace.MonoTracer;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static top.iot.gateway.core.trace.DeviceTracer.*;
import static top.iot.gateway.core.trace.FluxTracer.create;

@Slf4j
@AllArgsConstructor
@Deprecated
public class DefaultSendToDeviceMessageHandler {

    private final String serverId;

    private final DeviceSessionManager sessionManager;

    private final MessageHandler handler;

    private final DeviceRegistry registry;

    private final DecodedClientMessageHandler decodedClientMessageHandler;

    public void startup() {

        //处理发往设备的消息
        handler.handleSendToDeviceMessage(serverId)
               .subscribe(message -> {
                   try {
                       if (message instanceof DeviceMessage) {
                           handleDeviceMessage(((DeviceMessage) message));
                       }
                   } catch (Throwable e) {
                       log.error("handle send to device message error {}", message, e);
                   }
               });

        //处理设备状态检查
        handler.handleGetDeviceState(serverId, deviceId -> Flux
                .from(deviceId)
                .map(id -> new DeviceStateInfo(id, sessionManager.sessionIsAlive(id) ? DeviceState.online : DeviceState.offline)));

    }

    protected void handleDeviceMessage(DeviceMessage message) {
        String deviceId = message.getDeviceId();
        DeviceSession session = sessionManager.getSession(deviceId);
        //在当前服务
        if (session != null) {
            doSend(message, session);
        } else {
            //判断子设备消息
            registry.getDevice(deviceId)
                    .flatMap(deviceOperator -> {
                        //获取上级设备
                        return deviceOperator
                                .getSelfConfig(DeviceConfigKey.parentGatewayId)
                                .flatMap(registry::getDevice);
                    })
                    .flatMap(operator -> {
                        ChildDeviceMessage children = new ChildDeviceMessage();
                        children.setDeviceId(operator.getDeviceId());
                        children.setMessageId(message.getMessageId());
                        children.setTimestamp(message.getTimestamp());
                        children.setChildDeviceId(deviceId);
                        children.setChildDeviceMessage(message);
                        // 没有传递header
                        // https://github.com/iot-gateway/iot-gateway-pro/issues/19
                        if (null != message.getHeaders()) {
                            Map<String, Object> newHeader = new ConcurrentHashMap<>(message.getHeaders());
                            newHeader.remove("productId");
                            newHeader.remove("deviceName");
                            children.setHeaders(newHeader);
                        }
                        message.addHeader(Headers.dispatchToParent, true);
                        ChildrenDeviceSession childrenDeviceSession = sessionManager.getSession(operator.getDeviceId(), deviceId);
                        if (null != childrenDeviceSession) {
                            doSend(children, childrenDeviceSession);
                            return Mono.just(true);
                        }
                        DeviceSession childrenSession = sessionManager.getSession(operator.getDeviceId());
                        if (null != childrenSession) {
                            doSend(children, childrenSession);
                            return Mono.just(true);
                        }
                        //回复离线
                        return doReply(createReply(deviceId, message).error(ErrorCode.CLIENT_OFFLINE));
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("device[{}] not connected,send message fail", message.getDeviceId());
                        return doReply(createReply(deviceId, message).error(ErrorCode.CLIENT_OFFLINE));
                    }))
                    //注入跟踪信息
                    .as(MonoTracer.createWith(message.getHeaders()))
                    .subscribe();

        }
    }

    protected DeviceMessageReply createReply(String deviceId, DeviceMessage message) {
        DeviceMessageReply reply;
        if (message instanceof RepayableDeviceMessage) {
            reply = ((RepayableDeviceMessage<?>) message).newReply();
        } else {
            reply = new AcknowledgeDeviceMessage();
        }
        reply.messageId(message.getMessageId()).deviceId(deviceId);
        return reply;
    }

    protected void doSend(DeviceMessage message, DeviceSession session) {
        DeviceSession fSession =DeviceSession.trace(session.unwrap(DeviceSession.class)) ;
        if (fSession.getOperator() == null) {
            log.warn("unsupported send message to {}", fSession);
            return;
        }
        String deviceId = message.getDeviceId();
        DeviceMessageReply reply = this.createReply(deviceId, message);
        AtomicBoolean alreadyReply = new AtomicBoolean(false);
        boolean forget = message.getHeader(Headers.sendAndForget).orElse(false);
        Mono<Boolean> handler = fSession
                .getOperator()
                .getProtocol()
                .flatMap(protocolSupport -> protocolSupport.getMessageCodec(fSession.getTransport()))
                .flatMapMany(codec -> codec.encode(new ToDeviceMessageContext() {
                    @Override
                    public Mono<Boolean> sendToDevice(@Nonnull EncodedMessage message) {
                        return fSession.send(message);
                    }

                    @Override
                    public Mono<Void> disconnect() {
                        return Mono.fromRunnable(() -> {
                            fSession.close();
                            sessionManager.unregister(fSession.getId());
                        });
                    }

                    @Nonnull
                    @Override
                    public DeviceSession getSession() {
                        return fSession;
                    }

                    @Override
                    public Mono<DeviceSession> getSession(String deviceId) {
                        return Mono.justOrEmpty(sessionManager.getSession(deviceId))
                                   .map(DeviceSession::trace);
                    }

                    @Nonnull
                    @Override
                    public Message getMessage() {
                        return message;
                    }

                    @Override
                    public DeviceOperator getDevice() {
                        return fSession.getOperator();
                    }

                    @Override
                    public Mono<DeviceOperator> getDevice(String deviceId) {
                        return registry.getDevice(deviceId);
                    }

                    @Nonnull
                    @Override
                    public Mono<Void> reply(@Nonnull Publisher<? extends DeviceMessage> replyMessage) {
                        alreadyReply.set(true);
                        return Flux.from(replyMessage)
                                   .flatMap(msg -> decodedClientMessageHandler.handleMessage(fSession.getOperator(), msg))
                                   .then();
                    }
                }))
                //跟踪encode
                .as(create(SpanName.encode(deviceId),
                           (span, msg) -> span.setAttribute(SpanKey.message, msg.toString())))
                .flatMap(fSession::send)
                .reduce((r1, r2) -> r1 && r2)
                .flatMap(success -> {
                    if (alreadyReply.get() || forget) {
                        return Mono.empty();
                    }
                    if (message.getHeader(Headers.async).orElse(false)) {
                        return doReply(reply.message(ErrorCode.REQUEST_HANDLING.getText())
                                            .code(ErrorCode.REQUEST_HANDLING.name())
                                            .success());
                    }
                    return Mono.just(true);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    //协议没处理断开连接消息
                    if (message instanceof DisconnectDeviceMessage) {
                        session.close();
                        sessionManager.unregister(session.getId());
                        return alreadyReply.get()
                                ? Mono.empty()
                                : doReply(createReply(deviceId, message).success());
                    } else {
                        return alreadyReply.get() || forget
                                ? Mono.empty()
                                : doReply(createReply(deviceId, message).error(ErrorCode.UNSUPPORTED_MESSAGE));
                    }
                }))
                .onErrorResume(error -> {
                    alreadyReply.set(true);
                    if (!(error instanceof DeviceOperationException) || forget) {
                        log.error(error.getMessage(), error);
                    }
                    return forget ? Mono.empty() : this.doReply(reply.error(error));
                });

        //自设备断开连接
        if (message instanceof ChildDeviceMessage && ((ChildDeviceMessage) message).getChildDeviceMessage() instanceof DisconnectDeviceMessage) {
            ChildDeviceMessage child = ((ChildDeviceMessage) message);
            DisconnectDeviceMessage msg = (DisconnectDeviceMessage) ((ChildDeviceMessage) message).getChildDeviceMessage();

            handler = registry
                    .getDevice(msg.getDeviceId())
                    .flatMap(operator -> operator
                            .getSelfConfig(DeviceConfigKey.selfManageState)
                            .filter(Boolean.FALSE::equals)
                            .map(self -> sessionManager
                                    .unRegisterChildren(deviceId, operator.getDeviceId())
                                    .then(doReply(reply.success()))
                            ))
                    .defaultIfEmpty(handler)
                    .flatMap(Function.identity());
        }

        handler
                .as(MonoTracer.createWith(message.getHeaders()))
                .subscribe();
    }


    private Mono<Boolean> doReply(DeviceMessageReply reply) {
        Mono<Boolean> then = Mono.just(true);
        if (reply instanceof ChildDeviceMessageReply) {
            Message message = ((ChildDeviceMessageReply) reply).getChildDeviceMessage();
            if (message instanceof DeviceMessageReply) {
                then = doReply(((DeviceMessageReply) message));
            }
        }
        return writeToMessage(reply)
                .flatMap(handler::reply)
                .as(mo -> {
                    if (log.isDebugEnabled()) {
                        return mo.doFinally(s -> log.debug("reply message {} ,[{}]", s, reply));
                    }
                    return mo;
                })
                .doOnError((error) -> log.error("reply message error", error))
                .then(then);
    }

}
