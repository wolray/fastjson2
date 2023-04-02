package com.alibaba.fastjson2.issues_1000;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.PropertyNamingStrategy;
import com.alibaba.fastjson2.filter.NameFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Issue1265 {
    @Test
    public void test() {
        Bean bean = new Bean();
        bean.userName = "a";
        bean.groupName = "b";

        NameFilter nameFilter = (Object object, String name, Object value) -> {
            if (object instanceof Bean && name.endsWith("Name")) {
                return PropertyNamingStrategy.SnakeCase.fieldName(name);
            }
            return name;
        };
        String str = JSON.toJSONString(bean, nameFilter);
        assertEquals("{\"groupId\":0,\"group_name\":\"b\",\"userId\":0,\"user_name\":\"a\"}", str);
    }

    public static class Bean {
        public int userId;
        public int groupId;

        public String userName;
        public String groupName;
    }
}
