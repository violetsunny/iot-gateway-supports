package top.iot.gateway.supports.official;

import com.alibaba.fastjson.JSON;
import reactor.core.publisher.Mono;
import top.iot.gateway.core.metadata.DeviceMetadata;
import top.iot.gateway.core.metadata.DeviceMetadataCodec;

/*
 {
  "id": "test",
  "name": "测试",
  "properties": [
    {
      "id": "name",
      "name": "名称",
      "valueType": {
        "type": "string"
      }
    }
  ],
  "functions": [
    {
      "id": "playVoice",
      "name": "播放声音",
      "inputs": [
        {
          "id": "text",
          "name": "文字内容",
          "valueType": {
            "type": "string"
          }
        }
      ],
      "output": {
        "type": "boolean"
      }
    }
  ],
  "events": [
    {
      "id": "temp_sensor",
      "name": "温度传感器",
      "valueType": {
        "type": "double"
      }
    },
    {
      "id": "fire_alarm",
      "name": "火警",
      "valueType": {
        "type": "object",
        "properties": [
          {
            "id": "location",
            "name": "地点",
            "valueType": {
              "type": "string"
            }
          },
          {
            "id": "lng",
            "name": "经度",
            "valueType": {
              "type": "double"
            }
          },
          {
            "id": "lat",
            "name": "纬度",
            "valueType": {
              "type": "double"
            }
          }
        ]
      }
    }
  ]
}
 */

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class IotGatewayDeviceMetadataCodec implements DeviceMetadataCodec {

    private static final IotGatewayDeviceMetadataCodec INSTANCE = new IotGatewayDeviceMetadataCodec();

    public static IotGatewayDeviceMetadataCodec getInstance() {
        return INSTANCE;
    }

    @Override
    public String getId() {
        return "iot-gateway";
    }

    public DeviceMetadata doDecode(String json) {
        return new IotGatewayDeviceMetadata(JSON.parseObject(json));
    }

    @Override
    public Mono<DeviceMetadata> decode(String source) {
        return Mono.just(doDecode(source));
    }

    public String doEncode(DeviceMetadata metadata) {
        return new IotGatewayDeviceMetadata(metadata).toJson().toJSONString();
    }

    @Override
    public Mono<String> encode(DeviceMetadata metadata) {
        return Mono.just(doEncode(metadata));
    }
}
