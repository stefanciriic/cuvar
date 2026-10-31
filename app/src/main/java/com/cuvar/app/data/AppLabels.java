package com.cuvar.app.data;

import android.content.pm.PackageManager;

import com.cuvar.app.i18n.Translit;

/** Naziv instalirane aplikacije, prijavljen da ostane kako jeste (bez ćirilice i prevoda). */
public final class AppLabels {
    private AppLabels() {
    }

    /** Naziv aplikacije, ili null ako nije instalirana. */
    public static String orNull(PackageManager pm, String pkg) {
        try {
            return Translit.keep(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Naziv aplikacije, ili naziv paketa ako nije instalirana. */
    public static String of(PackageManager pm, String pkg) {
        String label = orNull(pm, pkg);
        return label == null ? pkg : label;
    }
}
