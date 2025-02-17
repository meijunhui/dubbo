/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.config.spring.context;

import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;

/**
 * Dubbo spring initialization context object
 */
public class DubboSpringInitializationContext {

    private BeanDefinitionRegistry registry;

    private ConfigurableListableBeanFactory beanFactory;

    private ApplicationContext applicationContext;

    private ModuleModel moduleModel;

    private DubboBootstrap dubboBootstrap;

    private volatile boolean bound;

    public void markAsBound() {
        bound = true;
    }

    public BeanDefinitionRegistry getRegistry() {
        return registry;
    }

    void setRegistry(BeanDefinitionRegistry registry) {
        this.registry = registry;
    }

    public ConfigurableListableBeanFactory getBeanFactory() {
        return beanFactory;
    }

    void setBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public ApplicationModel getApplicationModel() {
        return (moduleModel == null) ? null : moduleModel.getApplicationModel();
    }

    public ModuleModel getModuleModel() {
        return moduleModel;
    }

    /**
     * Change the binding ModuleModel, the ModuleModel and DubboBootstrap must be matched.
     *
     * @see #setDubboBootstrap(DubboBootstrap)
     * @param moduleModel
     */
    public void setModuleModel(ModuleModel moduleModel) {
        if (bound) {
            throw new IllegalStateException("Cannot change ModuleModel after bound context");
        }
        this.moduleModel = moduleModel;
    }

    public DubboBootstrap getDubboBootstrap() {
        return dubboBootstrap;
    }

    /**
     * Change the binding DubboBootstrap instance, the ModuleModel and DubboBootstrap must be matched.
     * <p></p>
     * By default, DubboBoostrap is created using the ApplicationModel in which the ModuleModel resides.
     *
     * @see #setModuleModel(ModuleModel)
     * @param dubboBootstrap
     */
    public void setDubboBootstrap(DubboBootstrap dubboBootstrap) {
        if (bound) {
            throw new IllegalStateException("Cannot change DubboBootstrap after bound context");
        }
        this.dubboBootstrap = dubboBootstrap;
    }
}
