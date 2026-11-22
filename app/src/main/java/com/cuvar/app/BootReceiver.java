package com.cuvar.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.cuvar.app.ui.Safe;

/** Posle paljenja telefona ili ažuriranja Čuvara laki režim se sam ponovo pokreće (Pristupačnost to radi sama). */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        Safe.run("boot", () -> GuardService.startLight(context));
    }
}
