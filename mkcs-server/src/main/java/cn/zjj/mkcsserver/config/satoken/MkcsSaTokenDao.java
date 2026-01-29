package cn.zjj.mkcsserver.config.satoken;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.util.SaFoxUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Sa-Token 持久层实现类 - Redis版本
 * 
 * @author zjj
 */
@Component
public class MkcsSaTokenDao implements SaTokenDao {


    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public MkcsSaTokenDao(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
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

    // ----------------- Object 读写 -----------------

    @Override
    public Object getObject(String key) {
        if (key == null) {
            return null;
        }
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getObject(String key, Class<T> cs) {
        if (key == null || cs == null) {
            return null;
        }
        
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj == null) {
            return null;
        }
        
        // 如果已经是目标类型，直接返回
        if (cs.isInstance(obj)) {
            return (T) obj;
        }
        
        // 尝试 JSON 反序列化
        try {
            if (obj instanceof String) {
                return objectMapper.readValue((String) obj, cs);
            }
            // 其他类型转换
            return objectMapper.convertValue(obj, cs);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to " + cs.getName(), e);
        }
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        if (key == null || object == null) {
            return;
        }
        if (timeout == SaTokenDao.NEVER_EXPIRE) {
            redisTemplate.opsForValue().set(key, object);
        } else {
            redisTemplate.opsForValue().set(key, object, Duration.ofSeconds(timeout));
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
        // 初始化操作，如果需要的话
    }

    @Override
    public void destroy() {
        // 销毁操作，如果需要的话
    }
}