//? if >=1.21.2 {
package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.RecipeMatchGetterAccessorMixin;

/** Semantic ownership bridge for the CachedCheck now held by the tick-function closure. */
public interface CampfireRecipeState {
    void anticrash$bindRecipeGetter(RecipeMatchGetterAccessorMixin getter);
}
//?}
