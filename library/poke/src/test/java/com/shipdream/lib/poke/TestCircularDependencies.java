/*
 * Copyright 2016 Kejun Xia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shipdream.lib.poke;

import com.shipdream.lib.poke.exception.CircularDependenciesException;
import com.shipdream.lib.poke.exception.PokeException;
import com.shipdream.lib.poke.exception.ProvideException;
import com.shipdream.lib.poke.exception.ProviderConflictException;
import com.shipdream.lib.poke.exception.ProviderMissingException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Singleton;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
public class TestCircularDependencies extends BaseTestCases {
    private ScopeCache scopeCache;
    private Component component;
    private Graph graph;

    @Before
    public void setUp() throws Exception {
        scopeCache = new ScopeCache();
        component = new Component(scopeCache);
        graph = new Graph();
        graph.addProviderFinder(component);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void should_not_throw_circular_dependency_exception_on_finite_circular_dependency() throws PokeException {
        Provider.OnInjectedListener<Power> powerOnInject = mock(Provider.OnInjectedListener.class);
        ProviderByClassType powerProvider = new ProviderByClassType<>(Power.class, PowerImpl.class, scopeCache);
        powerProvider.registerOnInjectedListener(powerOnInject);

        Provider.OnInjectedListener<Driver> driverOnInject = mock(Provider.OnInjectedListener.class);
        ProviderByClassType driverProvider = new ProviderByClassType(Driver.class, DriverImpl.class, scopeCache);
        driverProvider.registerOnInjectedListener(driverOnInject);

        Provider.OnInjectedListener<Robot> robotOnInject = mock(Provider.OnInjectedListener.class);
        ProviderByClassType robotProvider = new ProviderByClassType(Robot.class, RobotImpl.class, scopeCache);
        robotProvider.registerOnInjectedListener(robotOnInject);

        component.register(powerProvider);
        component.register(robotProvider);
        component.register(driverProvider);

        Factory factory = new Factory();
        graph.inject(factory, MyInject.class);

        Assert.assertNotNull(factory);

        PowerImpl power = (PowerImpl) factory.power;
        Assert.assertNotNull(power);

        RobotImpl robot = (RobotImpl) power.robot;
        Assert.assertNotNull(robot);

        DriverImpl driver = (DriverImpl) robot.driver;
        Assert.assertNotNull(driver);
    }

    static class RobotImpl2 implements Robot {
        @MyInject
        private Driver driver;

        @MyInject
        private Power power;
    }

    static class DriverImpl2 implements Driver {
        @MyInject
        private Power power;

        @MyInject
        private Robot robot;
    }

    static class PowerImpl2 implements Power {

        @MyInject
        private Robot robot;

        @MyInject
        private Driver driver;

        public Robot getConnectedRobot() {
            return robot;
        }
    }

    @Test(expected = CircularDependenciesException.class)
    public void should_detect_infinite_circular_dependencies() throws ProvideException, CircularDependenciesException, ProviderMissingException, ProviderConflictException {
        //Create a new unscoped component
        Component c = new Component();
        graph.addProviderFinder(c);
        c.register(Power.class, PowerImpl2.class);
        c.register(Driver.class, DriverImpl2.class);
        c.register(Robot.class, RobotImpl2.class);

        Factory factory = new Factory();
        graph.inject(factory, MyInject.class);
    }

    @Test
    public void shouldNotifyInjectedCallbackWhenObjectFullyInjectedWithCircularDependencies() throws PokeException {
        final Factory factory = new Factory();

        ProviderByClassType powerProvider = new ProviderByClassType(Power.class, PowerImpl.class, scopeCache);
        powerProvider.registerOnInjectedListener(new Provider.OnInjectedListener<Power>() {
            @Override
            public void onInjected(Power object) {
                Assert.assertNotNull(((PowerImpl) object).robot);
            }
        });

        ProviderByClassType driverProvider = new ProviderByClassType(Driver.class, DriverImpl.class, scopeCache);
        driverProvider.registerOnInjectedListener(new Provider.OnInjectedListener<Driver>() {
            @Override
            public void onInjected(Driver object) {
                Assert.assertNotNull(((DriverImpl) object).power);
            }
        });

        ProviderByClassType robotProvider = new ProviderByClassType(Robot.class, RobotImpl.class, scopeCache);
        robotProvider.registerOnInjectedListener(new Provider.OnInjectedListener<Robot>() {
            @Override
            public void onInjected(Robot object) {
                Assert.assertNotNull(((RobotImpl) object).driver);
            }
        });

        component.register(powerProvider);
        component.register(robotProvider);
        component.register(driverProvider);

        graph.inject(factory, MyInject.class);
    }

    @Test
    public void shouldInjectObjectOnlyOnceWithCircularDependencies() throws PokeException, NoSuchFieldException {
        final Factory factory = new Factory();

        Provider.OnInjectedListener<Power> powerOnInject = mock(Provider.OnInjectedListener.class);
        ProviderByClassType powerProvider = new ProviderByClassType(Power.class, PowerImpl.class, scopeCache);
        powerProvider.registerOnInjectedListener(powerOnInject);

        Provider.OnInjectedListener<Driver> driverOnInject = mock(Provider.OnInjectedListener.class);
        ProviderByClassType driverProvider = new ProviderByClassType(Driver.class, DriverImpl.class, scopeCache);
        driverProvider.registerOnInjectedListener(driverOnInject);

        Provider.OnInjectedListener<Robot> robotOnInject = mock(Provider.OnInjectedListener.class);
        ProviderByClassType robotProvider = new ProviderByClassType(Robot.class, RobotImpl.class, scopeCache);
        robotProvider.registerOnInjectedListener(robotOnInject);

        component.register(powerProvider);
        component.register(robotProvider);
        component.register(driverProvider);

        graph.inject(factory, MyInject.class);

        verify(powerOnInject, times(1)).onInjected(any(Power.class));
        verify(driverOnInject, times(1)).onInjected(any(Driver.class));
        verify(robotOnInject, times(1)).onInjected(any(Robot.class));
    }

    @Test
    public void test_should_inject_and_release_correctly_on_single_object() throws PokeException {
        prepareInjection(scopeCache);

        final Factory factory = new Factory();

        graph.inject(factory, MyInject.class);
        Assert.assertFalse(scopeCache.cache.isEmpty());
        graph.release(factory, MyInject.class);
        Assert.assertTrue(scopeCache.cache.isEmpty());
    }

    @Test
    public void test_should_inject_and_release_correctly_on_multiple_objects() throws PokeException {
        final ScopeCache scopeCache = new ScopeCache();
        prepareInjection(scopeCache);

        final Factory factory1 = new Factory();
        final Factory factory2 = new Factory();

        graph.inject(factory1, MyInject.class);

        Provider<Power> powerProvider = graph.getProvider(Power.class, null);
        Provider<Driver> driverProvider = graph.getProvider(Driver.class, null);
        Provider<Robot> robotProvider = graph.getProvider(Robot.class, null);

        Assert.assertFalse(scopeCache.cache.isEmpty());

        graph.inject(factory2, MyInject.class);
        Assert.assertFalse(scopeCache.cache.isEmpty());

        graph.release(factory2, MyInject.class);
        Assert.assertFalse(scopeCache.cache.isEmpty());

        graph.release(factory1, MyInject.class);
        Assert.assertTrue(scopeCache.cache.isEmpty());

        Assert.assertTrue(powerProvider.owners.isEmpty());
        Assert.assertEquals(0, powerProvider.getReferenceCount());

        Assert.assertTrue(driverProvider.owners.isEmpty());
        Assert.assertEquals(0, driverProvider.getReferenceCount());

        Assert.assertTrue(robotProvider.owners.isEmpty());
        Assert.assertEquals(0, robotProvider.getReferenceCount());
    }

    @Test
    public void test_should_inject_and_release_correctly_even_with_same_cached_objects_multiple_times()
            throws PokeException {
        final ScopeCache scopeCache = new ScopeCache();
        prepareInjection(scopeCache);

        final Factory factory = new Factory();
        graph.inject(factory, MyInject.class);

        Provider<Power> powerProvider = graph.getProvider(Power.class, null);
        Provider<Driver> driverProvider = graph.getProvider(Driver.class, null);
        Provider<Robot> robotProvider = graph.getProvider(Robot.class, null);

        Assert.assertFalse(scopeCache.cache.isEmpty());

        graph.inject(factory, MyInject.class);
        Assert.assertFalse(scopeCache.cache.isEmpty());

        graph.release(factory, MyInject.class);
        Assert.assertFalse(scopeCache.cache.isEmpty());

        graph.release(factory, MyInject.class);
        Assert.assertTrue(scopeCache.cache.isEmpty());

        Assert.assertTrue(powerProvider.owners.isEmpty());
        Assert.assertEquals(0, powerProvider.getReferenceCount());

        Assert.assertTrue(driverProvider.owners.isEmpty());
        Assert.assertEquals(0, driverProvider.getReferenceCount());

        Assert.assertTrue(robotProvider.owners.isEmpty());
        Assert.assertEquals(0, robotProvider.getReferenceCount());
    }

    @Test
    public void test_should_inject_and_release_correctly_on_multiple_objects_even_with_same_cached_objects_multiple_times()
            throws PokeException {
        final ScopeCache scopeCache = new ScopeCache();
        prepareInjection(scopeCache);

        final Factory factory1 = new Factory();
        graph.inject(factory1, MyInject.class);

        Provider<Power> powerProvider = graph.getProvider(Power.class, null);
        Provider<Driver> driverProvider = graph.getProvider(Driver.class, null);
        Provider<Robot> robotProvider = graph.getProvider(Robot.class, null);

        Assert.assertFalse(scopeCache.cache.isEmpty());

        final Factory factory2 = new Factory();
        graph.inject(factory2, MyInject.class);
        Assert.assertFalse(scopeCache.cache.isEmpty());

        graph.inject(factory1, MyInject.class);
        Assert.assertFalse(scopeCache.cache.isEmpty());

        Assert.assertNotNull(factory1);
        Assert.assertNotNull(factory1.power);
        Assert.assertNotNull(factory1.driver);
        Assert.assertNotNull(((PowerImpl) factory1.power).driver);
        Assert.assertNotNull(((PowerImpl) factory1.power).robot);
        Assert.assertNotNull(((DriverImpl) factory1.driver).power);
        Assert.assertNotNull(((DriverImpl) factory1.driver).robot);
        Assert.assertNotNull(((RobotImpl) ((DriverImpl) factory1.driver).robot).driver);
        Assert.assertNotNull(((RobotImpl) ((DriverImpl) factory1.driver).robot).power);

        Assert.assertNotNull(factory2);
        Assert.assertNotNull(factory2.power);
        Assert.assertNotNull(factory2.driver);
        Assert.assertNotNull(((PowerImpl)factory2.power).driver);
        Assert.assertNotNull(((PowerImpl)factory2.power).robot);
        Assert.assertNotNull(((DriverImpl)factory2.driver).power);
        Assert.assertNotNull(((DriverImpl)factory2.driver).robot);
        Assert.assertNotNull(((RobotImpl)((DriverImpl) factory2.driver).robot).driver);
        Assert.assertNotNull(((RobotImpl)((DriverImpl) factory2.driver).robot).power);

        graph.release(factory1, MyInject.class);
        Assert.assertFalse(scopeCache.cache.isEmpty());

        Assert.assertNotNull(factory1);
        Assert.assertNotNull(factory1.power);
        Assert.assertNotNull(factory1.driver);
        Assert.assertNotNull(((PowerImpl) factory1.power).driver);
        Assert.assertNotNull(((PowerImpl) factory1.power).robot);
        Assert.assertNotNull(((DriverImpl) factory1.driver).power);
        Assert.assertNotNull(((DriverImpl) factory1.driver).robot);
        Assert.assertNotNull(((RobotImpl) ((DriverImpl) factory1.driver).robot).driver);
        Assert.assertNotNull(((RobotImpl) ((DriverImpl) factory1.driver).robot).power);

        Assert.assertNotNull(factory2);
        Assert.assertNotNull(factory2.power);
        Assert.assertNotNull(factory2.driver);
        Assert.assertNotNull(((PowerImpl)factory2.power).driver);
        Assert.assertNotNull(((PowerImpl)factory2.power).robot);
        Assert.assertNotNull(((DriverImpl)factory2.driver).power);
        Assert.assertNotNull(((DriverImpl)factory2.driver).robot);
        Assert.assertNotNull(((RobotImpl)((DriverImpl) factory2.driver).robot).driver);
        Assert.assertNotNull(((RobotImpl)((DriverImpl) factory2.driver).robot).power);

        graph.release(factory1, MyInject.class);
        Assert.assertFalse(scopeCache.cache.isEmpty());

        Assert.assertNotNull(factory1);
        Assert.assertNotNull(factory1.power);
        Assert.assertNotNull(factory1.driver);

        Assert.assertNotNull(factory2);
        Assert.assertNotNull(factory2.power);
        Assert.assertNotNull(factory2.driver);
        Assert.assertNotNull(((PowerImpl)factory2.power).driver);
        Assert.assertNotNull(((PowerImpl)factory2.power).robot);
        Assert.assertNotNull(((DriverImpl)factory2.driver).power);
        Assert.assertNotNull(((DriverImpl)factory2.driver).robot);
        Assert.assertNotNull(((RobotImpl)((DriverImpl) factory2.driver).robot).driver);
        Assert.assertNotNull(((RobotImpl)((DriverImpl) factory2.driver).robot).power);

        graph.release(factory1, MyInject.class);
        Assert.assertFalse(scopeCache.cache.isEmpty());

        Assert.assertNotNull(factory1);
        Assert.assertNotNull(factory1.power);
        Assert.assertNotNull(factory1.driver);

        Assert.assertNotNull(factory2);
        Assert.assertNotNull(factory2.power);
        Assert.assertNotNull(factory2.driver);
        Assert.assertNotNull(((PowerImpl)factory2.power).driver);
        Assert.assertNotNull(((PowerImpl)factory2.power).robot);
        Assert.assertNotNull(((DriverImpl)factory2.driver).power);
        Assert.assertNotNull(((DriverImpl)factory2.driver).robot);
        Assert.assertNotNull(((RobotImpl)((DriverImpl) factory2.driver).robot).driver);
        Assert.assertNotNull(((RobotImpl)((DriverImpl) factory2.driver).robot).power);

        graph.release(factory2, MyInject.class);
        Assert.assertTrue(scopeCache.cache.isEmpty());

        Assert.assertTrue(powerProvider.owners.isEmpty());
        Assert.assertEquals(0, powerProvider.getReferenceCount());

        Assert.assertTrue(driverProvider.owners.isEmpty());
        Assert.assertEquals(0, driverProvider.getReferenceCount());

        Assert.assertTrue(robotProvider.owners.isEmpty());
        Assert.assertEquals(0, robotProvider.getReferenceCount());
    }

    private void prepareInjection(ScopeCache scopeCache) throws PokeException {
        ProviderByClassType powerProvider = new ProviderByClassType(Power.class, PowerImpl.class, scopeCache);

        ProviderByClassType driverProvider = new ProviderByClassType(Driver.class, DriverImpl.class, scopeCache);

        ProviderByClassType robotProvider = new ProviderByClassType(Robot.class, RobotImpl.class, scopeCache);

        component.register(powerProvider);
        component.register(robotProvider);
        component.register(driverProvider);
    }

    @Singleton
    static class Factory {
        @MyInject
        private Power power;

        @MyInject
        private Driver driver;
    }

    @Singleton
    static class RobotImpl implements Robot {
        @MyInject
        private Driver driver;

        @MyInject
        private Power power;
    }

    @Singleton
    static class DriverImpl implements Driver {
        @MyInject
        private Power power;

        @MyInject
        private Robot robot;
    }

    @Singleton
    static class PowerImpl implements Power {

        @MyInject
        private Robot robot;

        @MyInject
        private Driver driver;

        public Robot getConnectedRobot() {
            return robot;
        }
    }

    interface Robot {
    }

    interface Driver {
    }

    interface Power {
    }
}
