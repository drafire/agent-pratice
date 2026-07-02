package com.drafire.interceptor;

import com.drafire.tools.ToolFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class ToolRegistry implements BeanFactoryPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);

    private final Set<String> toolNames = new LinkedHashSet<>();

    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(toolNames);
    }

    public boolean contains(String beanName) {
        return toolNames.contains(beanName);
    }

    public String[] toArray() {
        return toolNames.toArray(new String[0]);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
            String factoryBeanName = bd.getFactoryBeanName();
            String factoryMethodName = bd.getFactoryMethodName();

            if (factoryBeanName == null || factoryMethodName == null) {
                continue;
            }

            try {
                Class<?> configClass = beanFactory.getType(factoryBeanName);
                if (configClass == null) {
                    continue;
                }
                java.lang.reflect.Method method = configClass.getDeclaredMethod(factoryMethodName);
                if (method.isAnnotationPresent(ToolFunction.class)) {
                    toolNames.add(beanName);
                    logger.info("发现 AI 工具: {}", beanName);
                }
            } catch (NoSuchMethodException e) {
                logger.debug("跳过 Bean '{}': 方法 {} 未找到", beanName, factoryMethodName);
            }
        }
        logger.info("AI 工具注册完成，共 {} 个: {}", toolNames.size(), toolNames);
    }
}