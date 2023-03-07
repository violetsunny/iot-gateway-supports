package top.iot.gateway.supports;

import com.google.common.collect.Maps;
import org.openjdk.jol.info.ClassLayout;

import java.util.Collections;

public class JolTest {
    public static void main(String[] args) {
        ClassLayout layout=ClassLayout.parseInstance(Maps.filterEntries(Collections.emptyMap(),entry -> true));

        System.out.println(layout.toPrintable());
    }
}
