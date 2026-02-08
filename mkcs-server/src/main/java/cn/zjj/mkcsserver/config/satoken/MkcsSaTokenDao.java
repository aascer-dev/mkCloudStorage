package cn.zjj.mkcsserver.config.satoken;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.util.SaFoxUtil;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.zjj.mkcscommon.json.JacksonObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Sa-Token 持久层实现类 - Redis版本 (针对复杂对象序列化优化)
 *
 * @author zjj
 */
@Component
public class MkcsSaTokenDao implements SaTokenDao {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 专用于 Redis 存储的 ObjectMapper
     * 必须开启 DefaultTyping，否则 SaSession 中的 List<String> 反序列化后会变成 List<Object> 或 LinkedHashMap，
     * 导致 hasPermission 鉴权失败。
     */
    private final ObjectMapper redisObjectMapper;

    public MkcsSaTokenDao(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;

        // 1. 基于你现有的配置创建一个新的 Mapper 实例
        this.redisObjectMapper = new JacksonObjectMapper();

        // 2. 关键：开启 DefaultTyping，保存多态类型信息
        // 指定序列化时将类名写入 JSON，例如：["java.util.ArrayList", ["user.add"]]
        this.redisObjectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
    }

    // ----------------- String 读写 -----------------

    @Override
    public String get(String key) {
        if (key == null) {
            return null;
        }
        Object value = redisTemplate.opsForValue().get(key);
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public void set(String key, String value, long timeout) {
        if (key == null || value == null) {
            return;
        }
        if (timeout == SaTokenDao.NEVER_EXPIRE) {
            redisTemplate.opsForValue().set(key, value);
        } else {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(timeout));
        }
    }

    @Override
    public void update(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        long expire = getTimeout(key);
        if (expire == SaTokenDao.NOT_VALUE_EXPIRE) {
            return;
        }
        this.set(key, value, expire);
    }

    @Override
    public void delete(String key) {
        if (key != null) {
            redisTemplate.delete(key);
        }
    }

    @Override
    public long getTimeout(String key) {
        if (key == null) {
            return SaTokenDao.NOT_VALUE_EXPIRE;
        }
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire == null ? SaTokenDao.NOT_VALUE_EXPIRE : expire;
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        if (key == null) {
            return;
        }
        if (timeout == SaTokenDao.NEVER_EXPIRE) {
            redisTemplate.persist(key);
        } else {
            redisTemplate.expire(key, Duration.ofSeconds(timeout));
        }
    }

    // ----------------- Object 读写 (核心修改部分) -----------------

    @Override
    public Object getObject(String key) {
        if (key == null) {
            return null;
        }
        Object obj = redisTemplate.opsForValue().get(key);
        // 如果 Redis 中存的是 JSON 字符串，尝试解析
        if (obj instanceof String) {
            try {
                // 读取为 Object，利用 DefaultTyping 自动推断类型
                return redisObjectMapper.readValue((String) obj, Object.class);
            } catch (Exception e) {
                // 如果解析失败，说明可能就是个普通字符串，直接返回
                return obj;
            }
        }
        return obj;
    }

    @Override
    public <T> T getObject(String key, Class<T> cs) {
        if (key == null || cs == null) {
            return null;
        }

        Object obj = redisTemplate.opsForValue().get(key);
        if (obj == null) {
            return null;
        }

        // 1. 如果已经是目标类型，直接返回
        if (cs.isInstance(obj)) {
            @SuppressWarnings("unchecked")
            T t = (T) obj;
            return t;
        }

        // 2. 如果是 JSON String，使用配置好的 Mapper 反序列化
        try {
            if (obj instanceof String) {
                return redisObjectMapper.readValue((String) obj, cs);
            }

            // 3. 兜底：如果是其他类型（如 LinkedHashMap），先转 JSON 再转目标对象
            // 这种情况通常发生在 RedisTemplate 默认序列化器已经做了一层转换，但类型不对
            String json = redisObjectMapper.writeValueAsString(obj);
            return redisObjectMapper.readValue(json, cs);

        } catch (Exception e) {
            throw new RuntimeException("SaTokenDao Object反序列化失败，Key: " + key, e);
        }
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        if (key == null || object == null) {
            return;
        }
        try {
            // 重点：手动序列化为 JSON 字符串存储
            // 这样可以确保我们的 activateDefaultTyping 配置生效，保存类型信息
            String jsonValue = redisObjectMapper.writeValueAsString(object);

            if (timeout == SaTokenDao.NEVER_EXPIRE) {
                redisTemplate.opsForValue().set(key, jsonValue);
            } else {
                redisTemplate.opsForValue().set(key, jsonValue, Duration.ofSeconds(timeout));
            }
        } catch (Exception e) {
            throw new RuntimeException("SaTokenDao Object序列化失败，Key: " + key, e);
        }
    }

    @Override
    public void updateObject(String key, Object object) {
        if (key == null || object == null) {
            return;
        }
        long expire = getObjectTimeout(key);
        if (expire == SaTokenDao.NOT_VALUE_EXPIRE) {
            return;
        }
        this.setObject(key, object, expire);
    }

    @Override
    public void deleteObject(String key) {
        if (key != null) {
            redisTemplate.delete(key);
        }
    }

    @Override
    public long getObjectTimeout(String key) {
        if (key == null) {
            return SaTokenDao.NOT_VALUE_EXPIRE;
        }
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire == null ? SaTokenDao.NOT_VALUE_EXPIRE : expire;
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        if (key == null) {
            return;
        }
        if (timeout == SaTokenDao.NEVER_EXPIRE) {
            redisTemplate.persist(key);
        } else {
            redisTemplate.expire(key, Duration.ofSeconds(timeout));
        }
    }

    // ----------------- Session 读写 -----------------

    @Override
    public SaSession getSession(String sessionId) {
        return getObject(sessionId, SaSession.class);
    }

    @Override
    public void setSession(SaSession session, long timeout) {
        if (session != null && session.getId() != null) {
            setObject(session.getId(), session, timeout);
        }
    }

    @Override
    public void updateSession(SaSession session) {
        if (session != null && session.getId() != null) {
            updateObject(session.getId(), session);
        }
    }

    @Override
    public void deleteSession(String sessionId) {
        deleteObject(sessionId);
    }

    @Override
    public long getSessionTimeout(String sessionId) {
        return getObjectTimeout(sessionId);
    }

    @Override
    public void updateSessionTimeout(String sessionId, long timeout) {
        updateObjectTimeout(sessionId, timeout);
    }

    // ----------------- 会话管理 -----------------

    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        if (prefix == null) {
            prefix = "";
        }
        if (keyword == null) {
            keyword = "";
        }

        Set<String> keys = redisTemplate.keys(prefix + "*" + keyword + "*");
        if (keys == null) {
            return new ArrayList<>();
        }

        List<String> list = new ArrayList<>(keys);
        return SaFoxUtil.searchList(list, start, size, sortType);
    }

    // ----------------- 生命周期 -----------------

    @Override
    public void init() {
        // 初始化操作
    }

    @Override
    public void destroy() {
        // 销毁操作
    }
}