package com.axiom.api.script;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation every script class must declare.
 * ScriptLoader uses this at runtime to discover available scripts.
 *
 * Usage:
 *   @ScriptManifest(name = "Axiom Woodcutting", category = ScriptCategory.SKILLING, version = "1.0")
 *   public class WoodcuttingScript extends BotScript { ... }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ScriptManifest
{
    String name();
    String version() default "1.0";
    ScriptCategory category();
    String author() default "Axiom";
    String description() default "";
}
