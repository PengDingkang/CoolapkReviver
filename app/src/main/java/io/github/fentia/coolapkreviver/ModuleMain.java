package io.github.fentia.coolapkreviver;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

// 把酷安出站请求头里的版本标识改写成服务端认可的新版本，让旧版客户端的 v6 接口恢复可用。
// 版本头统一由 AppConfig 生成、经 OkHttp 写入，因此直接 hook OkHttp 即可，无需依赖酷安的混淆类名。
public final class ModuleMain extends XposedModule {

    private static final String TAG = "CoolapkReviver";
    private static final String TARGET_PACKAGE = "com.coolapk.market";

    private static final String VERSION_NAME = "16.6.4";
    private static final String VERSION_CODE = "2609291";
    private static final String API_VERSION = "16";
    private static final String APP_SUPPORTED = "2310232";

    private static final Pattern UA_VERSION = Pattern.compile("CoolMarket/[0-9][0-9A-Za-z.]*-[0-9]+");

    private String processName;
    private boolean hooked;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        processName = param.getProcessName();
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName()) || hooked) {
            return;
        }
        hooked = true;

        boolean mainProcess = processName == null || TARGET_PACKAGE.equals(processName);
        try {
            Class<?> builder = Class.forName("okhttp3.Request$Builder", false, param.getClassLoader());
            hook(builder.getDeclaredMethod("header", String.class, String.class)).intercept(this::intercept);
            hook(builder.getDeclaredMethod("addHeader", String.class, String.class)).intercept(this::intercept);
            if (mainProcess) {
                log(Log.INFO, TAG, "hook ok: " + VERSION_NAME + " / " + VERSION_CODE + " / api " + API_VERSION);
            }
        } catch (Throwable t) {
            if (mainProcess) {
                log(Log.ERROR, TAG, "hook failed: " + t);
            }
        }
    }

    private Object intercept(XposedInterface.Chain chain) throws Throwable {
        Object name = chain.getArg(0);
        Object value = chain.getArg(1);
        if (!(name instanceof String) || value == null) {
            return chain.proceed();
        }
        String replaced = map((String) name, String.valueOf(value));
        if (replaced == null || replaced.equals(value)) {
            return chain.proceed();
        }
        return chain.proceed(new Object[]{name, replaced});
    }

    private String map(String name, String value) {
        switch (name) {
            case "X-App-Version":
                return VERSION_NAME;
            case "X-App-Code":
                return VERSION_CODE;
            case "X-Api-Version":
                return API_VERSION;
            case "X-App-Supported":
                return APP_SUPPORTED;
            case "User-Agent":
                Matcher matcher = UA_VERSION.matcher(value);
                return matcher.find()
                        ? matcher.replaceFirst("CoolMarket/" + VERSION_NAME + "-" + VERSION_CODE)
                        : null;
            default:
                return null;
        }
    }
}
