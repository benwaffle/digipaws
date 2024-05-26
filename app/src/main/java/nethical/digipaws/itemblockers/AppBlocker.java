package nethical.digipaws.itemblockers;
import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nethical.digipaws.data.ServiceData;
import nethical.digipaws.utils.DelayManager;
import nethical.digipaws.utils.DigiConstants;
import nethical.digipaws.utils.DigiUtils;
import nethical.digipaws.utils.OverlayManager;
import nethical.digipaws.utils.OverlayManager;
import nethical.digipaws.utils.SurvivalModeManager;

public class AppBlocker {
    
    public static void performAction(ServiceData data){
        if(isSettingsBlockerOn(data.getService()) && data.getPackageName()==DigiConstants.SETTINGS_PACKAGE_NAME){
            DigiUtils.pressHome(data.getService());
            return;
        }
        if(SurvivalModeManager.isSurvivalModeActive(data.getService())){
            DigiUtils.pressHome(data.getService());
            return;
        }
        
        
        SharedPreferences sharedPreferences = data.getService().getSharedPreferences(DigiConstants.PREF_BLOCKED_APPS_FILE,
					Context.MODE_PRIVATE);
       
        List<String> blockedApps = new ArrayList<>(sharedPreferences.getStringSet(DigiConstants.PREF_BLOCKED_APPS_LIST_KEY,new HashSet<>()));
        
        
        for (String packageName : blockedApps) {
            if(packageName.equals(data.getPackageName())){
                if(DelayManager.isWarningDelayOver(data.getService(),data.getBlockerId())){
                    if(DelayManager.isOverlayCooldownActive(data.getService())){
                            DigiUtils.pressBack(data.getService());
                            break;
                        }
                        DigiUtils.pressHome(data.getService());
                        OverlayManager overlayManager = new OverlayManager(data.getService(),data.getBlockerId());
                        overlayManager.showSMUseCoinsOverlay(data);
                        DelayManager.updateOverlayCooldown(data.getService());
                        break;
                    }
                    break;
                    }
        }

    }
    
    private static boolean isSettingsBlockerOn(AccessibilityService service){
        SharedPreferences sharedPreferences = service.getSharedPreferences(DigiConstants.PREF_APPBLOCKER_CONFIG_FILE,
		Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(DigiConstants.PREF_APPBLOCKER_SETTINGS_BLOCKER_KEY,false);
    }
}
