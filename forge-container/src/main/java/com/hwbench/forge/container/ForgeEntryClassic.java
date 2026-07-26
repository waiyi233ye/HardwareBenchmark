package com.hwbench.forge.container;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Forge Universal Container Entry for Forge 1.12.2 - 1.20.1 (net.minecraftforge.fml era).
 *
 * <p>This class carries ONLY the {@code @net.minecraftforge.fml.common.Mod}
 * annotation and an {@code @Mod.EventHandler} method whose parameter type
 * is {@code net.minecraftforge.fml.common.event.FMLServerStartingEvent}.
 * On Forge 1.12.2 - 1.20.1, both the annotation type and the event type
 * exist on the classpath, so {@link Class#getDeclaredMethods()} resolves cleanly.
 *
 * <p>On Forge 1.7.10 (where {@code net.minecraftforge.fml} doesn't exist),
 * Forge's ASM scanner doesn't recognise the
 * {@code @net.minecraftforge.fml.common.Mod} annotation descriptor, so
 * this class is never loaded as a mod — {@link ForgeEntryLegacy} takes over.
 *
 * <p>On NeoForge 1.20.2+ (where {@code net.neoforged.fml.common.Mod} replaces
 * {@code net.minecraftforge.fml.common.Mod}), Forge's scanner doesn't
 * recognise this annotation either, so {@link ForgeEntryNeo} takes over.
 *
 * <p><b>Note on 1.16.5+ event forwarding:</b> For Forge 1.16.5+, the
 * container registers event-bus listeners via reflection in
 * {@link #registerModernEventListeners()} (called from the constructor),
 * because Forge 1.18+'s {@code ASMEventHandler$ASMClassLoader} can't see
 * classes injected into {@code TransformingClassLoader}. The delegate
 * ({@code HWBenchForgeLegacy} / {@code HWBenchForge}) no longer
 * self-registers on {@code MinecraftForge.EVENT_BUS}. The
 * {@code @Mod.EventHandler} method below is still needed for 1.12.2
 * (where the delegate uses {@code @Mod.EventHandler} for command
 * registration).
 *
 * <p>Compiled with Java 8 (temurin-8) so the .class file loads on Java 8 JVMs
 * (required by Forge 1.12.2).
 */
@net.minecraftforge.fml.common.Mod(
        value = "hwbench",
        modid = "hwbench",
        name = "HardwareBenchmark",
        version = "2.0.0",
        acceptableRemoteVersions = "*"
)
public class ForgeEntryClassic extends ForgeContainerBase {

    public ForgeEntryClassic() {
        initContainer();
        if (delegate != null) {
            registerModernEventListeners();
        }
    }

    /**
     * Forge 1.12.2 - 1.20.1 server-starting event handler.
     * Forwards {@code FMLServerStartingEvent} to the sub-JAR's
     * {@code HWBenchForge1122.onServerStarting} (1.12.2) or
     * {@code HWBenchForgeLegacy.onServerStarting} (1.16.5).
     */
    @net.minecraftforge.fml.common.Mod.EventHandler
    public void onServerStarting(net.minecraftforge.fml.common.event.FMLServerStartingEvent event) {
        log("1.12.2-1.20.1 FMLServerStartingEvent received, forwarding to delegate");
        forwardEvent(event);
    }

    /**
     * Registers modern event-bus listeners (Forge 1.16+) via reflection.
     *
     * <p>On Forge 1.18+, the delegate's {@code @SubscribeEvent} self-registration
     * in {@code MinecraftForge.EVENT_BUS.register(this)} fails because Forge's
     * {@code ASMEventHandler$ASMClassLoader} parent is {@code ModuleClassLoader}
     * (from {@code cpw.mods.securejarhandler}), NOT the
     * {@code TransformingClassLoader} where the delegate's classes were injected.
     * The ASM-generated listener classes can't be found, causing
     * {@code ClassNotFoundException}, so the {@code /hwbench} command is never
     * registered.
     *
     * <p>This method works around that by registering listeners from the
     * container entry class (which IS loaded by Forge's mod classloader /
     * {@code ModuleClassLoader} and therefore visible to
     * {@code ASMEventHandler$ASMClassLoader}). Each listener is a
     * {@link Consumer} proxy that forwards the event to the delegate via
     * {@link #forwardEventToDelegate(String, Object)}.
     *
     * <p>On Forge 1.7.10/1.12.2 (where {@code net.minecraftforge.common.MinecraftForge}
     * doesn't exist or the modern event bus isn't available), this method
     * returns silently — those versions use the legacy {@code @Mod.EventHandler}
     * path via {@link #onServerStarting(net.minecraftforge.fml.common.event.FMLServerStartingEvent)}.
     */
    private void registerModernEventListeners() {
        try {
            Class<?> forgeClass;
            try {
                forgeClass = Class.forName("net.minecraftforge.common.MinecraftForge");
            } catch (Throwable notFound) {
                // Forge 1.7.10 / 1.12.2 — no modern event bus; legacy path handles it.
                return;
            }
            Field eventBusField = forgeClass.getField("EVENT_BUS");
            Object eventBus = eventBusField.get(null);

            // CRITICAL: RegisterCommandsEvent registers the /hwbench command.
            registerListener(eventBus,
                    "net.minecraftforge.event.RegisterCommandsEvent",
                    "onRegisterCommands");
            registerListener(eventBus,
                    "net.minecraftforge.event.server.ServerStartingEvent",
                    "onServerStarting");
            registerListener(eventBus,
                    "net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent",
                    "onPlayerLogin");
            log("Registered modern event-bus listeners (Forge 1.16+) via reflection");
        } catch (Throwable t) {
            log("registerModernEventListeners failed: " + t);
            try {
                t.printStackTrace(System.err);
            } catch (Throwable ignored) { /* ignore */ }
        }
    }

    /**
     * Registers a single event listener on the Forge event bus via reflection.
     *
     * <p>Forge's event bus API has evolved across versions:
     * <ul>
     *   <li><b>Forge 1.16.5</b>: {@code IEventBus.addListener(Class, Consumer)} —
     *       public 2-param method taking event class and consumer.</li>
     *   <li><b>Forge 1.18+</b>: The public 2-param {@code addListener(Class, Consumer)}
     *       method was REMOVED. The public API only has {@code addListener(Consumer)}
     *       (with type inference from the Consumer's generic superclass) and
     *       {@code addListener(EventPriority, Consumer)} variants. The event type
     *       inference doesn't work with {@link Proxy}-based Consumers (their
     *       generic superclass is {@code Object}, not {@code Consumer<T>}).
     *       However, Forge's {@code EventBus} class has a PRIVATE
     *       {@code addListener(EventPriority, boolean, Class, Consumer)} method
     *       that takes the event class explicitly — we invoke this via reflection.</li>
     * </ul>
     *
     * <p>This method tries multiple signatures in order:
     * <ol>
     *   <li>Public {@code addListener(Class, Consumer)} — Forge 1.16.5</li>
     *   <li>Private {@code addListener(EventPriority, boolean, Class, Consumer)} —
     *       Forge 1.18+ (found by walking the class hierarchy of the event bus's
     *       runtime class, e.g. {@code net.minecraftforge.eventbus.EventBus})</li>
     * </ol>
     *
     * @param eventBus           the Forge {@code IEventBus} instance (from
     *                           {@code MinecraftForge.EVENT_BUS})
     * @param eventClassName     the FQN of the event class (e.g.
     *                           {@code net.minecraftforge.event.RegisterCommandsEvent})
     * @param delegateMethodName the name of the delegate method to invoke
     *                           (e.g. {@code onRegisterCommands})
     */
    private void registerListener(Object eventBus, String eventClassName,
                                  String delegateMethodName) {
        try {
            Class<?> eventClass = Class.forName(eventClassName);

            // Create the Consumer proxy upfront — it's the same for all method signatures.
            final String methodName = delegateMethodName;
            Object consumerProxy = Proxy.newProxyInstance(
                    Consumer.class.getClassLoader(),
                    new Class<?>[]{Consumer.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if (method.getName().equals("accept") && args != null && args.length == 1) {
                                forwardEventToDelegate(methodName, args[0]);
                                return null;
                            }
                            if (method.getName().equals("equals") && args != null && args.length == 1) {
                                return proxy == args[0];
                            }
                            if (method.getName().equals("hashCode")) {
                                return System.identityHashCode(proxy);
                            }
                            if (method.getName().equals("toString")) {
                                return "HWBenchEventListener[" + methodName + "]";
                            }
                            return null;
                        }
                    });

            // ---- Approach 1: public addListener(Class, Consumer) — Forge 1.16.5 ----
            Method addListenerMethod = null;
            Object[] invokeArgs = null;
            for (Method m : eventBus.getClass().getMethods()) {
                if (!m.getName().equals("addListener")) continue;
                Class<?>[] paramTypes = m.getParameterTypes();
                if (paramTypes.length != 2) continue;
                if (paramTypes[0] != Class.class) continue;
                if (!Consumer.class.isAssignableFrom(paramTypes[1])) continue;
                addListenerMethod = m;
                invokeArgs = new Object[]{eventClass, consumerProxy};
                break;
            }

            // ---- Approach 2: private addListener(EventPriority, boolean, Class, Consumer) — Forge 1.18+ ----
            if (addListenerMethod == null) {
                Class<?> eventPriorityClass = null;
                // EventPriority is in net.minecraftforge.eventbus.api (not net.minecraftforge.eventbus)
                for (String epFqn : new String[]{
                        "net.minecraftforge.eventbus.api.EventPriority",   // Forge 1.18+
                        "net.minecraftforge.eventbus.EventPriority"        // older Forge variants (fallback)
                }) {
                    try {
                        eventPriorityClass = Class.forName(epFqn);
                        break;
                    } catch (Throwable notFound) {
                        // try next
                    }
                }
                if (eventPriorityClass == null) {
                    log("EventPriority class not found; cannot use Forge 1.18+ private addListener");
                }
                if (eventPriorityClass != null) {
                    Object normalPriority = null;
                    try {
                        @SuppressWarnings({"rawtypes", "unchecked"})
                        Object ep = Enum.valueOf((Class<Enum>) eventPriorityClass, "NORMAL");
                        normalPriority = ep;
                    } catch (Throwable t) {
                        log("Could not get EventPriority.NORMAL: " + t);
                    }
                    if (normalPriority != null) {
                        // Walk the class hierarchy searching for the private 4-param method.
                        // getDeclaredMethods() returns methods declared ONLY on the current class
                        // (including private), so we must walk up the hierarchy ourselves.
                        Class<?> c = eventBus.getClass();
                        while (c != null && addListenerMethod == null) {
                            for (Method m : c.getDeclaredMethods()) {
                                if (!m.getName().equals("addListener")) continue;
                                Class<?>[] paramTypes = m.getParameterTypes();
                                if (paramTypes.length != 4) continue;
                                if (paramTypes[0] != eventPriorityClass) continue;
                                if (paramTypes[1] != boolean.class) continue;
                                if (paramTypes[2] != Class.class) continue;
                                if (!Consumer.class.isAssignableFrom(paramTypes[3])) continue;
                                addListenerMethod = m;
                                invokeArgs = new Object[]{normalPriority, Boolean.FALSE, eventClass, consumerProxy};
                                break;
                            }
                            c = c.getSuperclass();
                        }
                    }
                }
            }

            if (addListenerMethod == null) {
                log("Could not find any addListener method on event bus for "
                        + eventClassName + " (delegate method: " + delegateMethodName + ")");
                return;
            }

            addListenerMethod.setAccessible(true);
            addListenerMethod.invoke(eventBus, invokeArgs);
            log("Registered listener for " + eventClassName
                    + " -> delegate." + delegateMethodName
                    + " (via " + addListenerMethod.getDeclaringClass().getName()
                    + "." + addListenerMethod.getName() + ")");
        } catch (Throwable t) {
            log("registerListener failed for " + eventClassName
                    + " (delegate method: " + delegateMethodName + "): " + t);
            try {
                t.printStackTrace(System.err);
            } catch (Throwable ignored) { /* ignore */ }
        }
    }

    /**
     * Forwards an event object to a named method on the delegate via reflection.
     *
     * <p>This is the modern-event-bus counterpart to
     * {@link ForgeContainerBase#forwardEvent(Object)}, which only forwards
     * {@code FMLServerStartingEvent} to {@code onServerStarting}. This method
     * walks the delegate's class hierarchy (including superclasses) and invokes
     * the first method whose name matches and whose single parameter is
     * assignable from the event's class.
     *
     * @param methodName the name of the delegate method to invoke
     * @param event      the event object to pass as the method argument
     */
    private void forwardEventToDelegate(String methodName, Object event) {
        if (delegate == null) {
            log("Cannot forward event to delegate." + methodName
                    + "() — delegate is null (init may have failed)");
            return;
        }
        try {
            Class<?> eventClass = event.getClass();
            Class<?> c = delegate.getClass();
            while (c != null) {
                for (Method m : c.getDeclaredMethods()) {
                    if (!m.getName().equals(methodName)) continue;
                    if (m.getParameterCount() != 1) continue;
                    try {
                        Class<?>[] params = m.getParameterTypes();
                        if (params[0].isAssignableFrom(eventClass)) {
                            m.setAccessible(true);
                            m.invoke(delegate, event);
                            return;
                        }
                    } catch (Throwable t) {
                        // Parameter type resolution may fail for methods whose param type
                        // doesn't exist on this classpath — skip those silently.
                    }
                }
                c = c.getSuperclass();
            }
            // Not finding a matching method is not necessarily an error —
            // e.g. onRegisterCommands may have been removed in a future delegate.
            log("No matching " + methodName + " method found on delegate for event "
                    + eventClass.getName());
        } catch (Throwable t) {
            log("Failed to forward event to delegate." + methodName + "(): " + t);
            try {
                t.printStackTrace(System.err);
            } catch (Throwable ignored) { /* ignore */ }
        }
    }
}
