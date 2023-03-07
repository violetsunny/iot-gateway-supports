package top.iot.gateway.supports.device.session;

import top.iot.gateway.core.device.session.DeviceSessionInfo;
import top.iot.gateway.core.server.session.DeviceSession;
import top.iot.gateway.core.utils.Reactors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LocalDeviceSessionManager extends AbstractDeviceSessionManager {

    public static LocalDeviceSessionManager create(){
        return new LocalDeviceSessionManager();
    }

    @Override
    public String getCurrentServerId() {
        return "local";
    }

    @Override
    protected Mono<Boolean> initSessionConnection(DeviceSession session) {
        return Reactors.ALWAYS_FALSE;
    }

    @Override
    protected Mono<Long> removeRemoteSession(String deviceId) {
        return Reactors.ALWAYS_ZERO_LONG;
    }

    @Override
    protected Mono<Long> getRemoteTotalSessions() {
        return Reactors.ALWAYS_ZERO_LONG;
    }

    @Override
    protected Mono<Boolean> remoteSessionIsAlive(String deviceId) {
        return Reactors.ALWAYS_FALSE;
    }

    @Override
    protected Mono<Boolean> checkRemoteSessionIsAlive(String deviceId) {
        return Reactors.ALWAYS_FALSE;
    }

    @Override
    protected Flux<DeviceSessionInfo> remoteSessions(String serverId) {
        return Flux.empty();
    }
}
