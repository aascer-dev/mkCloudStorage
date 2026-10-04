package cn.zjj.mkcsserver;

import com.zjj.mkcscommon.utils.CommonUtils;
import org.junit.jupiter.api.Test;

public class TestUUIDGenrate {

    @Test
    public void testUUIDGenrate() {
        System.out.println(CommonUtils.generateUUID());
    }
}
