package com.paddle.ocr.demo.plugin;

// Tasker 插件 API 工具类
// See Also: http://tasker.dinglisch.net/plugins.html

import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

public class TaskerPlugin {

    private final static String 	TAG = "TaskerPlugin";

    private final static String 	BASE_KEY = "net.dinglisch.android.tasker";

    private final static String 	EXTRAS_PREFIX = BASE_KEY + ".extras.";

    private final static int		FIRST_ON_FIRE_VARIABLES_TASKER_VERSION = 80;

    public final static String		VARIABLE_PREFIX = "%";

    private final static int		RANDOM_HISTORY_SIZE = 100;

    /**
     * 	Action that the EditActivity for an event plugin should be launched by
     */
    public final static String 		ACTION_EDIT_EVENT = BASE_KEY + ".ACTION_EDIT_EVENT";

    private final static String		VARIABLE_NAME_START_EXPRESSION =  "[\\w&&[^_]]";
    private final static String		VARIABLE_NAME_MID_EXPRESSION =  "[\\w0-9]+";
    private final static String		VARIABLE_NAME_END_EXPRESSION =  "[\\w0-9&&[^_]]";

    public final static String		VARIABLE_NAME_MAIN_PART_MATCH_EXPRESSION =
            VARIABLE_NAME_START_EXPRESSION + VARIABLE_NAME_MID_EXPRESSION + VARIABLE_NAME_END_EXPRESSION
            ;

    public final static String		VARIABLE_NAME_MATCH_EXPRESSION =
            VARIABLE_PREFIX + "+" +
                    VARIABLE_NAME_MAIN_PART_MATCH_EXPRESSION
            ;

    private static Pattern			VARIABLE_NAME_MATCH_PATTERN = null;

    /**
     *	@see #addVariableBundle(Bundle, Bundle)
     *	@see Host#getVariablesBundle(Bundle)
     */
    private final static String		EXTRA_VARIABLES_BUNDLE = EXTRAS_PREFIX + "VARIABLES";

    /**
     * 	Host capabilities, passed to plugin with edit intents
     */
    private final static String		EXTRA_HOST_CAPABILITIES = EXTRAS_PREFIX + "HOST_CAPABILITIES";

    /**
     *  @see Setting#hostSupportsVariableReturn(Bundle)
     */
    public final static int			EXTRA_HOST_CAPABILITY_SETTING_RETURN_VARIABLES = 2;

    /**
     *	@see Condition#hostSupportsVariableReturn(Bundle)
     */
    public final static int			EXTRA_HOST_CAPABILITY_CONDITION_RETURN_VARIABLES = 4;

    /**
     * 	@see Setting#hostSupportsOnFireVariableReplacement(Bundle)
     */
    public final static int			EXTRA_HOST_CAPABILITY_SETTING_FIRE_VARIABLE_REPLACEMENT = 8;

    /**
     * @see Setting#hostSupportsVariableReturn(Bundle)
     */
    private final static int		EXTRA_HOST_CAPABILITY_RELEVANT_VARIABLES = 16;

    public final static int			EXTRA_HOST_CAPABILITY_SETTING_SYNCHRONOUS_EXECUTION = 32;

    public final static int			EXTRA_HOST_CAPABILITY_REQUEST_QUERY_DATA_PASS_THROUGH = 64;

    public final static int			EXTRA_HOST_CAPABILITY_ENCODING_JSON = 128;

    public final static int			EXTRA_HOST_CAPABILITY_ALL =
            EXTRA_HOST_CAPABILITY_SETTING_RETURN_VARIABLES |
                    EXTRA_HOST_CAPABILITY_CONDITION_RETURN_VARIABLES |
                    EXTRA_HOST_CAPABILITY_SETTING_FIRE_VARIABLE_REPLACEMENT |
                    EXTRA_HOST_CAPABILITY_RELEVANT_VARIABLES|
                    EXTRA_HOST_CAPABILITY_SETTING_SYNCHRONOUS_EXECUTION |
                    EXTRA_HOST_CAPABILITY_REQUEST_QUERY_DATA_PASS_THROUGH |
                    EXTRA_HOST_CAPABILITY_ENCODING_JSON
            ;

    /**
     * Possible encodings of text in bundle values
     *
     * @see #setKeyEncoding(Bundle,String[],Encoding)
     */
    public enum Encoding { JSON }

    private final static String		BUNDLE_KEY_ENCODING_JSON_KEYS = BASE_KEY + ".JSON_ENCODED_KEYS";

    public static boolean hostSupportsKeyEncoding( Bundle extrasFromHost, Encoding encoding ) {
        switch ( encoding ) {
            case JSON:
                return hostSupports( extrasFromHost, EXTRA_HOST_CAPABILITY_ENCODING_JSON );
            default:
                return false;
        }
    }

    private final static String		EXTRA_HINTS_BUNDLE = EXTRAS_PREFIX + "HINTS";

    private final static String		BUNDLE_KEY_HINT_PREFIX = ".hints.";

    private final static String		BUNDLE_KEY_HINT_TIMEOUT_MS = BUNDLE_KEY_HINT_PREFIX + "TIMEOUT";

    private final static String	BUNDLE_KEY_RELEVANT_VARIABLES = BASE_KEY + ".RELEVANT_VARIABLES";

    public static boolean hostSupportsRelevantVariables( Bundle extrasFromHost ) {
        return hostSupports( extrasFromHost,  EXTRA_HOST_CAPABILITY_RELEVANT_VARIABLES );
    }

    /**
     * Specifies to host which variables might be used by the plugin.
     *
     * Used in EditActivity, before setResult().
     *
     * @param  intentToHost the intent being returned to the host
     * @param  variableNames array of relevant variable names
     */
    public static void addRelevantVariableList( Intent intentToHost, String [] variableNames ) {
        intentToHost.putExtra( BUNDLE_KEY_RELEVANT_VARIABLES, variableNames );
    }

    /**
     * Validate a variable name.
     *
     * The basic requirement for variables from a plugin is that they must be all lower-case.
     *
     * @param  varName name to check
     */
    public static boolean variableNameValid( String varName ) {
        boolean validFlag = false;
        if ( varName == null )
            Log.d( TAG, "variableNameValid: null name" );
        else {
            if ( VARIABLE_NAME_MATCH_PATTERN == null )
                VARIABLE_NAME_MATCH_PATTERN = Pattern.compile( VARIABLE_NAME_MATCH_EXPRESSION, 0 );
            if ( VARIABLE_NAME_MATCH_PATTERN.matcher( varName ).matches() ) {
                if ( variableNameIsLocal( varName ) )
                    validFlag = true;
                else
                    Log.d( TAG, "variableNameValid: name not local: " + varName );
            }
            else
                Log.d( TAG, "variableNameValid: invalid name: " + varName );
        }
        return validFlag;
    }

    /**
     * Allows the plugin/host to indicate to each other a set of variables which they are referencing.
     */
    public static String [] getRelevantVariableList( Bundle fromHostIntentExtras ) {
        String [] relevantVars = (String []) getBundleValueSafe( fromHostIntentExtras, BUNDLE_KEY_RELEVANT_VARIABLES, String [].class, "getRelevantVariableList" );
        if ( relevantVars == null )
            relevantVars = new String [0];
        return relevantVars;
    }

    /**
     * Used by: plugin QueryReceiver, FireReceiver
     *
     * Add a bundle of variable name/value pairs.
     * Names must be valid Tasker local variable names (with % prefix).
     * Values must be String.
     *
     * @param resultExtras the result extras from the receiver onReceive (from a call to getResultExtras())
     * @param variables the variables to send
     */
    public static void addVariableBundle( Bundle resultExtras, Bundle variables ) {
        resultExtras.putBundle( EXTRA_VARIABLES_BUNDLE, variables );
    }

    /**
     * Specify the encoding for a set of bundle keys.
     */
    public static void setKeyEncoding( Bundle resultBundleToHost, String [] keys, Encoding encoding ) {
        if ( Encoding.JSON.equals( encoding ) )
            addStringArrayToBundleAsString(
                    keys, resultBundleToHost, BUNDLE_KEY_ENCODING_JSON_KEYS, "setValueEncoding"
            );
        else
            Log.e( TAG, "unknown encoding: " + encoding );
    }

    // ----------------------------- SETTING PLUGIN ONLY --------------------------------- //

    public static class Setting {

        public final static String		VARNAME_ERROR_MESSAGE = VARIABLE_PREFIX + "errmsg";

        private final static String		BUNDLE_KEY_VARIABLE_REPLACE_STRINGS = EXTRAS_PREFIX + "VARIABLE_REPLACE_KEYS";

        private final static String 	EXTRA_REQUESTED_TIMEOUT = EXTRAS_PREFIX + "REQUESTED_TIMEOUT";

        public final static int 		REQUESTED_TIMEOUT_MS_NONE = 0;

        public final static int 		REQUESTED_TIMEOUT_MS_MAX = 3599000;

        public final static int 		REQUESTED_TIMEOUT_MS_NEVER = REQUESTED_TIMEOUT_MS_MAX + 1000;

        private final static String 	EXTRA_PLUGIN_COMPLETION_INTENT = EXTRAS_PREFIX + "COMPLETION_INTENT";

        public final static String 		EXTRA_RESULT_CODE = EXTRAS_PREFIX + "RESULT_CODE";

        public final static String EXTRA_CALL_SERVICE_PACKAGE = BASE_KEY + ".EXTRA_CALL_SERVICE_PACKAGE";
        public final static String EXTRA_CALL_SERVICE = BASE_KEY + ".EXTRA_CALL_SERVICE";
        public final static String EXTRA_CALL_SERVICE_FOREGROUND = BASE_KEY + ".EXTRA_CALL_SERVICE_FOREGROUND";

        public final static int	RESULT_CODE_OK = Activity.RESULT_OK;
        public final static int	RESULT_CODE_OK_MINOR_FAILURES = Activity.RESULT_FIRST_USER;
        public final static int	RESULT_CODE_FAILED = Activity.RESULT_FIRST_USER + 1;
        public final static int	RESULT_CODE_PENDING = Activity.RESULT_FIRST_USER + 2;
        public final static int	RESULT_CODE_UNKNOWN = Activity.RESULT_FIRST_USER + 3;

        public final static int	RESULT_CODE_FAILED_PLUGIN_FIRST = Activity.RESULT_FIRST_USER + 9;

        /**
         * Used by: plugin EditActivity.
         *
         * Indicates to plugin that host will replace variables in specified bundle keys.
         */
        public static boolean hostSupportsOnFireVariableReplacement( Bundle extrasFromHost ) {
            return hostSupports( extrasFromHost, EXTRA_HOST_CAPABILITY_SETTING_FIRE_VARIABLE_REPLACEMENT );
        }

        /**
         * Used by: plugin EditActivity.
         *
         * This version also includes backwards compatibility with pre 4.2 Tasker versions.
         */
        public static boolean hostSupportsOnFireVariableReplacement( Activity editActivity ) {
            boolean supportedFlag = hostSupportsOnFireVariableReplacement( editActivity.getIntent().getExtras() );
            if ( ! supportedFlag ) {
                ComponentName callingActivity = editActivity.getCallingActivity();
                if ( callingActivity == null )
                    Log.w( TAG, "hostSupportsOnFireVariableReplacement: null callingActivity, defaulting to false" );
                else {
                    String callerPackage = callingActivity.getPackageName();
                    supportedFlag =
                            ( callerPackage.startsWith( BASE_KEY ) ) &&
                                    ( getPackageVersionCode( editActivity.getPackageManager(), callerPackage ) > FIRST_ON_FIRE_VARIABLES_TASKER_VERSION )
                            ;
                }
            }
            return supportedFlag;
        }

        public static boolean hostSupportsSynchronousExecution( Bundle extrasFromHost ) {
            return hostSupports( extrasFromHost, EXTRA_HOST_CAPABILITY_SETTING_SYNCHRONOUS_EXECUTION );
        }

        /**
         * Request the host to wait the specified number of milliseconds before continuing.
         *
         * Used in EditActivity, before setResult().
         *
         * @param  intentToHost the intent being returned to the host
         * @param  timeoutMS timeout in milliseconds
         */
        public static void requestTimeoutMS( Intent intentToHost, int timeoutMS ) {
            if ( timeoutMS < 0 )
                Log.w( TAG, "requestTimeoutMS: ignoring negative timeout (" + timeoutMS + ")" );
            else {
                if (
                        ( timeoutMS > REQUESTED_TIMEOUT_MS_MAX ) &&
                                ( timeoutMS != REQUESTED_TIMEOUT_MS_NEVER )
                        ) {
                    Log.w( TAG, "requestTimeoutMS: requested timeout " + timeoutMS + " exceeds maximum, setting to max (" + REQUESTED_TIMEOUT_MS_MAX + ")" );
                    timeoutMS = REQUESTED_TIMEOUT_MS_MAX;
                }
                intentToHost.putExtra( EXTRA_REQUESTED_TIMEOUT, timeoutMS );
            }
        }

        /**
         * Used by: plugin EditActivity
         *
         * Indicates to host which bundle keys should be replaced.
         */
        public static void setVariableReplaceKeys( Bundle resultBundleToHost, String [] listOfKeyNames ) {
            addStringArrayToBundleAsString(
                    listOfKeyNames, resultBundleToHost, BUNDLE_KEY_VARIABLE_REPLACE_STRINGS,
                    "setVariableReplaceKeys"
            );
        }

        /**
         * Used by: plugin FireReceiver
         *
         * Indicates to plugin whether the host will process variables which it passes back
         */
        public static boolean hostSupportsVariableReturn( Bundle extrasFromHost ) {
            return hostSupports( extrasFromHost, EXTRA_HOST_CAPABILITY_SETTING_RETURN_VARIABLES );
        }

        /**
         * Used by: plugin FireReceiver
         *
         * Tell the host that the plugin has finished execution.
         * This should only be used if RESULT_CODE_PENDING was returned by FireReceiver.onReceive().
         *
         * @param context the context for starting service / sending broadcast
         * @param originalFireIntent the intent received from the host (via onReceive())
         * @param resultCode level of success in performing the settings
         * @param vars any variables that the plugin wants to set in the host
         */
        public static boolean signalFinish( Context context, Intent originalFireIntent, int resultCode, Bundle vars ) {
            String errorPrefix = "signalFinish: ";
            boolean okFlag = false;

            String completionIntentString = (String) getExtraValueSafe( originalFireIntent, Setting.EXTRA_PLUGIN_COMPLETION_INTENT, String.class, "signalFinish" );

            if ( completionIntentString != null ) {
                Uri completionIntentUri = null;
                try {
                    completionIntentUri = Uri.parse( completionIntentString );
                }
                catch ( Exception e ) {
                    Log.w( TAG, errorPrefix + "couldn't parse " + completionIntentString );
                }

                if ( completionIntentUri != null ) {
                    try {
                        Intent completionIntent = Intent.parseUri( completionIntentString, Intent.URI_INTENT_SCHEME );

                        completionIntent.putExtra( EXTRA_RESULT_CODE, resultCode );

                        if ( vars != null )
                            completionIntent.putExtra( EXTRA_VARIABLES_BUNDLE, vars );

                        String callServicePackage = (String) getExtraValueSafe(completionIntent, Setting.EXTRA_CALL_SERVICE_PACKAGE, String.class, "signalFinish");
                        String callService = (String) getExtraValueSafe(completionIntent, Setting.EXTRA_CALL_SERVICE, String.class, "signalFinish");
                        Boolean foreground = (Boolean) getExtraValueSafe(completionIntent, Setting.EXTRA_CALL_SERVICE_FOREGROUND, Boolean.class, "signalFinish");
                        if (callServicePackage != null && callService != null && foreground != null) {
                            completionIntent.setComponent(new ComponentName(callServicePackage, callService));
                            if (foreground && android.os.Build.VERSION.SDK_INT >= 26) {
                                context.startForegroundService(completionIntent);
                            } else {
                                context.startService(completionIntent);
                            }
                        } else {
                            context.sendBroadcast(completionIntent);
                        }

                        okFlag = true;
                    }
                    catch ( URISyntaxException e ) {
                        Log.w( TAG, errorPrefix + "bad URI: " + completionIntentUri );
                    }
                }
            }

            return okFlag;
        }

        /**
         * Check for a hint on the timeout value the host is using.
         */
        public static int getHintTimeoutMS( Bundle extrasFromHost ) {
            int timeoutMS = -1;
            Bundle hintsBundle = (Bundle) TaskerPlugin.getBundleValueSafe( extrasFromHost, EXTRA_HINTS_BUNDLE, Bundle.class, "getHintTimeoutMS" );
            if ( hintsBundle != null ) {
                Integer val = (Integer) getBundleValueSafe( hintsBundle, BUNDLE_KEY_HINT_TIMEOUT_MS, Integer.class, "getHintTimeoutMS" );
                if ( val != null )
                    timeoutMS = val;
            }
            return timeoutMS;
        }
    }

    // ----------------------------- CONDITION/EVENT PLUGIN ONLY --------------------------------- //

    public static class Condition {
        public final static String EXTRA_RESULT_RECEIVER = BASE_KEY + ".EXTRA_RESULT_RECEIVER";

        public static boolean hostSupportsVariableReturn( Bundle extrasFromHost ) {
            return hostSupports( extrasFromHost,  EXTRA_HOST_CAPABILITY_CONDITION_RETURN_VARIABLES );
        }

        public static ResultReceiver getResultReceiver(Intent intentFromHost) {
            if (intentFromHost == null) return null;
            return (ResultReceiver) getExtraValueSafe(intentFromHost, EXTRA_RESULT_RECEIVER, ResultReceiver.class, "getResultReceiver");
        }
    }

    // ----------------------------- EVENT PLUGIN ONLY --------------------------------- //

    /**
     * 用于 Event 插件（如系统事件、定时事件触发的插件）。
     * 对应的接收器需继承 BroadcastReceiver 并处理 FIRE_SETTING action。
     * 参考 Termux:Tasker 的 Event 内部类实现。
     */
    public static class Event {

        /**
         * Bundle key for pass-through data message ID.
         */
        private final static String PASS_THROUGH_BUNDLE_MESSAGE_ID_KEY = BASE_KEY + ".PASS_THROUGH_BUNDLE_MESSAGE_ID";

        /**
         * Extra for host to request query pass-through data.
         */
        private final static String EXTRA_REQUEST_QUERY_PASS_THROUGH_DATA = EXTRAS_PREFIX + "REQUEST_QUERY_PASS_THROUGH_DATA";

        /**
         * Used by: plugin FireReceiver (onReceive)
         *
         * Check if the host supports query pass-through data requests.
         *
         * @param extrasFromHost extras from the host intent
         * @return true if host supports request query pass-through data
         */
        public static boolean hostSupportsRequestQueryDataPassThrough(Bundle extrasFromHost) {
            return hostSupports(extrasFromHost, EXTRA_HOST_CAPABILITY_REQUEST_QUERY_DATA_PASS_THROUGH);
        }

        /**
         * Used by: plugin FireReceiver (onReceive)
         *
         * Add pass-through data to the intent that is sent back to the host.
         * The host can use this data to pass through to other plugins or tasks.
         *
         * @param requestQueryIntent the intent that will be sent back to the host
         * @param data the data to pass through
         */
        public static void addPassThroughData(Intent requestQueryIntent, Bundle data) {
            Bundle passThroughBundle = retrieveOrCreatePassThroughBundle(requestQueryIntent);
            if (data != null) {
                passThroughBundle.putAll(data);
            }
            requestQueryIntent.putExtra(EXTRA_REQUEST_QUERY_PASS_THROUGH_DATA, true);
        }

        /**
         * Used by: plugin QueryReceiver (onReceive)
         *
         * Retrieve pass-through data from the host's request intent.
         *
         * @param requestQueryIntent the intent received from the host
         * @return Bundle containing the pass-through data, or null if not present
         */
        public static Bundle retrievePassThroughData(Intent requestQueryIntent) {
            Bundle extras = requestQueryIntent.getExtras();
            if (extras == null) return null;
            Bundle passThroughBundle = extras.getBundle(PASS_THROUGH_BUNDLE_MESSAGE_ID_KEY);
            if (passThroughBundle == null) return null;
            Bundle data = passThroughBundle.getBundle(PASS_THROUGH_BUNDLE_MESSAGE_ID_KEY + ".data");
            return data;
        }

        /**
         * Used by: plugin FireReceiver (onReceive)
         *
         * Add a non-repeating message ID to pass-through data for verification.
         *
         * @param requestQueryIntent the intent that will be sent back to the host
         */
        public static void addPassThroughMessageID(Intent requestQueryIntent) {
            Bundle passThroughBundle = retrieveOrCreatePassThroughBundle(requestQueryIntent);
            passThroughBundle.putInt(
                    PASS_THROUGH_BUNDLE_MESSAGE_ID_KEY + ".id",
                    getPositiveNonRepeatingRandomInteger()
            );
        }

        /**
         * Used by: plugin QueryReceiver (onReceive)
         *
         * Retrieve the message ID from pass-through data.
         *
         * @param requestQueryIntent the intent received from the host
         * @return the message ID, or -1 if not found
         */
        public static int retrievePassThroughMessageID(Intent requestQueryIntent) {
            Bundle extras = requestQueryIntent.getExtras();
            if (extras == null) return -1;
            Bundle passThroughBundle = extras.getBundle(PASS_THROUGH_BUNDLE_MESSAGE_ID_KEY);
            if (passThroughBundle == null) return -1;
            return passThroughBundle.getInt(PASS_THROUGH_BUNDLE_MESSAGE_ID_KEY + ".id", -1);
        }

        /**
         * Retrieve or create the pass-through bundle in the intent.
         */
        private static Bundle retrieveOrCreatePassThroughBundle(Intent intent) {
            Bundle extras = intent.getExtras();
            if (extras == null) {
                extras = new Bundle();
                intent.replaceExtras(extras);
            }
            Bundle passThroughBundle = extras.getBundle(PASS_THROUGH_BUNDLE_MESSAGE_ID_KEY);
            if (passThroughBundle == null) {
                passThroughBundle = new Bundle();
                extras.putBundle(PASS_THROUGH_BUNDLE_MESSAGE_ID_KEY, passThroughBundle);
            }
            return passThroughBundle;
        }
    }

    // ----------------------------- HOST PLUGIN ONLY --------------------------------- //

    /**
     * Host 端接口：用于 Tasker（或兼容宿主）向插件传递能力标志、完成 Intent 等信息。
     * 参考 Termux:Tasker 的 Host 内部类实现。
     */
    public static class Host {

        /**
         * Used by: host app (Tasker)
         *
         * Add capability flags to the intent sent to the plugin.
         *
         * @param intentToPlugin the intent being sent to the plugin
         * @param capabilities bitwise OR of capability flags
         */
        public static void addCapabilities(Intent intentToPlugin, int capabilities) {
            intentToPlugin.putExtra(EXTRA_HOST_CAPABILITIES, capabilities);
        }

        /**
         * Used by: host app (Tasker)
         *
         * Add a completion intent to the fire intent so the plugin can signal back.
         * Supports callService (with optional foreground), startForegroundService, and sendBroadcast.
         *
         * @param fireIntent the intent being sent to the plugin receiver
         * @param completionIntent the intent the plugin should use to signal completion
         */
        public static void addCompletionIntent(Intent fireIntent, Intent completionIntent) {
            String packageName = completionIntent.getPackage();
            String componentName = null;
            if (completionIntent.getComponent() != null) {
                componentName = completionIntent.getComponent().getClassName();
            }
            if (packageName != null && componentName != null) {
                // callService mode (Android Service)
                completionIntent.putExtra(Setting.EXTRA_CALL_SERVICE_PACKAGE, packageName);
                completionIntent.putExtra(Setting.EXTRA_CALL_SERVICE, componentName);
                completionIntent.putExtra(Setting.EXTRA_CALL_SERVICE_FOREGROUND, false);
            } else {
                // sendBroadcast mode (default)
                // No extra service info needed
            }
            String completionIntentString = completionIntent.toUri(Intent.URI_INTENT_SCHEME);
            fireIntent.putExtra(Setting.EXTRA_PLUGIN_COMPLETION_INTENT, completionIntentString);
        }

        /**
         * Used by: host app (Tasker)
         *
         * Extract the result code from a completion intent.
         *
         * @param completionIntent the intent used for completion signalling
         * @return the result code, or -1 if not found
         */
        public static int getSettingResultCode(Intent completionIntent) {
            return completionIntent.getIntExtra(Setting.EXTRA_RESULT_CODE, -1);
        }

        /**
         * Used by: host app (Tasker)
         *
         * Extract the variables bundle from a completion intent.
         *
         * @param completionIntent the intent used for completion signalling
         * @return the variables bundle, or null if not found
         */
        public static Bundle getVariablesBundle(Intent completionIntent) {
            Bundle extras = completionIntent.getExtras();
            if (extras == null) return null;
            return extras.getBundle(EXTRA_VARIABLES_BUNDLE);
        }

        /**
         * Used by: host app (Tasker)
         *
         * Add a timeout hint to the fire intent so the plugin knows the expected timeout.
         *
         * @param intentToPlugin the intent being sent to the plugin
         * @param timeoutMS the timeout hint in milliseconds
         */
        public static void addHintTimeoutMS(Intent intentToPlugin, int timeoutMS) {
            Bundle hintsBundle = new Bundle();
            hintsBundle.putInt(BUNDLE_KEY_HINT_TIMEOUT_MS, timeoutMS);
            intentToPlugin.putExtra(EXTRA_HINTS_BUNDLE, hintsBundle);
        }

        /**
         * Used by: host app (Tasker)
         *
         * Get the set of keys with a specific encoding from a bundle.
         *
         * @param resultBundle the bundle containing the results
         * @param encoding the encoding to check for
         * @return array of key names with the specified encoding
         */
        public static String[] getKeysWithEncoding(Bundle resultBundle, Encoding encoding) {
            if (Encoding.JSON.equals(encoding)) {
                return getStringArrayFromBundleString(resultBundle, BUNDLE_KEY_ENCODING_JSON_KEYS, "getKeysWithEncoding");
            }
            return new String[0];
        }
    }

    private static Object getBundleValueSafe( Bundle b, String key, Class<?> expectedClass, String funcName ) {
        Object value = null;
        if ( b != null ) {
            if ( b.containsKey( key ) ) {
                Object obj = b.get( key );
                if ( obj == null )
                    Log.w( TAG, funcName + ": " + key + ": null value" );
                else if ( obj.getClass() != expectedClass )
                    Log.w( TAG, funcName + ": " + key + ": expected " + expectedClass.getClass().getName() + ", got " + obj.getClass().getName() );
                else
                    value = obj;
            }
        }
        return value;
    }

    private static Object getExtraValueSafe( Intent i, String key, Class<?> expectedClass, String funcName ) {
        return ( i.hasExtra( key ) ) ?
                getBundleValueSafe( i.getExtras(), key, expectedClass, funcName ) :
                null;
    }

    private static boolean hostSupports( Bundle extrasFromHost, int capabilityFlag ) {
        Integer flags = (Integer) getBundleValueSafe( extrasFromHost, EXTRA_HOST_CAPABILITIES, Integer.class, "hostSupports" );
        return
                ( flags != null ) &&
                        ( ( flags & capabilityFlag ) > 0 )
                ;
    }

    public static int getPackageVersionCode( PackageManager pm, String packageName ) {
        int code = -1;
        if ( pm != null ) {
            try {
                PackageInfo pi = pm.getPackageInfo( packageName, 0 );
                if ( pi != null )
                    code = pi.versionCode;
            }
            catch ( Exception e ) {
                Log.e( TAG, "getPackageVersionCode: exception getting package info" );
            }
        }
        return code;
    }

    private static boolean variableNameIsLocal( String varName ) {
        int digitCount = 0;
        int length = varName.length();
        for ( int x = 0; x < length; x++ ) {
            char ch = varName.charAt( x );
            if ( Character.isUpperCase( ch ) )
                return false;
            else if ( Character.isDigit( ch ) )
                digitCount++;
        }
        if ( digitCount == ( varName.length() - 1 ) )
            return false;
        return true;
    }

    private static String [] getStringArrayFromBundleString( Bundle bundle, String key, String funcName ) {
        String spec = (String) getBundleValueSafe( bundle, key, String.class, funcName );
        String [] toReturn = null;
        if ( spec != null )
            toReturn = spec.split( " " );
        return toReturn;
    }

    private static void addStringArrayToBundleAsString( String [] toAdd, Bundle bundle, String key, String callerName ) {
        StringBuilder builder = new StringBuilder();
        if ( toAdd != null ) {
            for ( String keyName : toAdd ) {
                if ( keyName.contains( " " ) )
                    Log.w( TAG, callerName + ": ignoring bad keyName containing space: " + keyName );
                else {
                    if ( builder.length() > 0 )
                        builder.append( ' ' );
                    builder.append( keyName );
                }
            }
            if ( builder.length() > 0 )
                bundle.putString( key, builder.toString() );
        }
    }

    private static int [] 		lastRandomsSeen = null;
    private static int 			randomInsertPointer = 0;
    private static SecureRandom sr = null;

    public static int getPositiveNonRepeatingRandomInteger() {
        if ( sr == null ) {
            sr = new SecureRandom();
            lastRandomsSeen = new int[RANDOM_HISTORY_SIZE];
            for ( int x = 0; x < lastRandomsSeen.length; x++ )
                lastRandomsSeen[x] = -1;
        }
        int toReturn;
        do {
            toReturn = sr.nextInt( Integer.MAX_VALUE );
            for ( int seen : lastRandomsSeen ) {
                if ( seen == toReturn ) {
                    toReturn = -1;
                    break;
                }
            }
        }
        while ( toReturn == -1 );
        lastRandomsSeen[randomInsertPointer] = toReturn;
        randomInsertPointer = ( randomInsertPointer + 1 ) % lastRandomsSeen.length;
        return toReturn;
    }
}