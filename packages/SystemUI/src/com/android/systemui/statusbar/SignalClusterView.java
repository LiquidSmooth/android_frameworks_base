/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.phone.PhoneStatusBar;

// Intimately tied to the design of res/layout/signal_cluster_view.xml
public class SignalClusterView
        extends LinearLayout
        implements NetworkController.SignalCluster, NetworkController.CarrierCluster {

    static final boolean DEBUG = false;
    static final String TAG = "SignalClusterView";

    NetworkController mNC;

    private boolean mWifiVisible = false;
    private int mWifiStrengthId = 0, mWifiActivityId = 0;
    private boolean mMobileVisible = false;
    private int mMobileStrengthId = 0, mMobileActivityId = 0, mMobileTypeId = 0;
    private boolean mIsAirplaneMode = false;
    private int mAirplaneIconId = 0;
    private int mCarrierIconId = -1;
    private String mWifiDescription, mMobileDescription, mMobileTypeDescription;
    private PhoneStatusBar mStatusBar;

    ViewGroup mWifiGroup, mMobileGroup;
    ImageView mWifi, mMobile, mWifiActivity, mMobileActivity, mMobileType, mAirplane;
    View mSpacer;

    private SettingsObserver mSettingsObserver;

    private boolean mCustomColor;
    private int systemColor;

    protected class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.CUSTOM_SYSTEM_ICON_COLOR), false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SYSTEM_ICON_COLOR), false, this, UserHandle.USER_ALL);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
	
    public static Drawable GrayscaleDrawable (Context context, Drawable drawable) {
    	int width = drawable.getIntrinsicWidth();
    	width = width > 0 ? width : 1;
    	int height = drawable.getIntrinsicHeight();
    	height = height > 0 ? height : 1;
    	Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
	Bitmap bitmap_gray = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    	Canvas canvas = new Canvas(bitmap); 
	Canvas canvas_gray = new Canvas(bitmap_gray); 
	drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
    	drawable.draw(canvas);
	Paint paint = new Paint();
	ColorMatrix colormatrix = new ColorMatrix();
	colormatrix.setSaturation(0);
	ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colormatrix);
	paint.setAntiAlias(true);
	paint.setColorFilter(filter); 
        canvas_gray.drawBitmap(bitmap, 0, 0, paint);
    	Drawable drawable_gray = new BitmapDrawable(context.getResources(), bitmap_gray);
        return drawable_gray;
    }

    public SignalClusterView(Context context) {
        this(context, null);
    }

    public SignalClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (mSettingsObserver == null) {
            mSettingsObserver = new SettingsObserver(new Handler());
        }
        mSettingsObserver.observe();
    }

    public void setStatusBar(PhoneStatusBar phoneStatusBar) {
        mStatusBar = phoneStatusBar;
    }

    public void setNetworkController(NetworkController nc) {
        if (DEBUG) Log.d(TAG, "NetworkController=" + nc);
        mNC = nc;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mWifiActivity   = (ImageView) findViewById(R.id.wifi_inout);
        mMobileGroup    = (ViewGroup) findViewById(R.id.mobile_combo);
        mMobile         = (ImageView) findViewById(R.id.mobile_signal);
        mMobileActivity = (ImageView) findViewById(R.id.mobile_inout);
        mMobileType     = (ImageView) findViewById(R.id.mobile_type);
        mSpacer         =             findViewById(R.id.spacer);
        mAirplane       = (ImageView) findViewById(R.id.airplane);

	
	if (mStatusBar != null) {
		mStatusBar.addIconToColor(mWifi);
		mStatusBar.addIconToColor(mMobile);
		mStatusBar.addIconToColor(mMobileType);
		mStatusBar.addIconToColor(mAirplane);
		mStatusBar.addIconToColor(mWifiActivity);
		mStatusBar.addIconToColor(mMobileActivity);
	} else {
		Log.d("SystemUIDebug","mStatusBar=null");
        }

        apply();
    }

    @Override
    protected void onDetachedFromWindow() {
        mWifiGroup      = null;
        mWifi           = null;
        mWifiActivity   = null;
        mMobileGroup    = null;
        mMobile         = null;
        mMobileActivity = null;
        mMobileType     = null;
        mSpacer         = null;
        mAirplane       = null;

        super.onDetachedFromWindow();
    }

    @Override
    public void setCarrierIndicators(int carrierIcon) {
        mCarrierIconId = carrierIcon;

        apply();
    }

    @Override
    public void setWifiIndicators(boolean visible, int strengthIcon, int activityIcon,
            String contentDescription) {
        mWifiVisible = visible;
        mWifiStrengthId = strengthIcon;
        mWifiActivityId = activityIcon;
        mWifiDescription = contentDescription;

        apply();
    }

    @Override
    public void setMobileDataIndicators(boolean visible, int strengthIcon, int activityIcon,
            int typeIcon, String contentDescription, String typeContentDescription) {
        mMobileVisible = visible;
        mMobileStrengthId = strengthIcon;
        mMobileActivityId = activityIcon;
        mMobileTypeId = typeIcon;
        mMobileDescription = contentDescription;
        mMobileTypeDescription = typeContentDescription;

        apply();
    }

    @Override
    public void setIsAirplaneMode(boolean is, int airplaneIconId) {
        mIsAirplaneMode = is;
        mAirplaneIconId = airplaneIconId;

        apply();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mWifiVisible && mWifiGroup != null && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        if (mMobileVisible && mMobileGroup != null && mMobileGroup.getContentDescription() != null)
            event.getText().add(mMobileGroup.getContentDescription());
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        if (mWifi != null) {
            mWifi.setImageDrawable(null);
        }
        if (mWifiActivity != null) {
            mWifiActivity.setImageDrawable(null);
        }

        if (mMobile != null) {
            mMobile.setImageDrawable(null);
        }
        if (mMobileActivity != null) {
            mMobileActivity.setImageDrawable(null);
        }
        if (mMobileType != null) {
            mMobileType.setImageDrawable(null);
        }

        if(mAirplane != null) {
            mAirplane.setImageDrawable(null);
        }

        apply();
    }

    // Run after each indicator change.
    private void apply() {
        if (mWifiGroup == null) return;

        if (mWifiVisible) {
            if (mWifiStrengthId != 0) {	
	    	Drawable wifiBitmap = mContext.getResources().getDrawable(mWifiStrengthId);
		if (mCustomColor) {
                	wifiBitmap=GrayscaleDrawable(mContext,wifiBitmap);
			wifiBitmap.setColorFilter(systemColor, Mode.MULTIPLY);
            	} else {
                	wifiBitmap.clearColorFilter();
            	}
            	mWifi.setImageDrawable(wifiBitmap);
	    }
 
	    if (mWifiActivityId != 0) {	          	
		Drawable mWifiActivityBitmap = mContext.getResources().getDrawable(mWifiActivityId);
	    	if (mCustomColor) {
			mWifiActivityBitmap=GrayscaleDrawable(mContext,mWifiActivityBitmap);
			mWifiActivityBitmap.setColorFilter(systemColor, Mode.MULTIPLY);
            	} else {
                	mWifiActivityBitmap.clearColorFilter();
            	}
		mWifiActivity.setImageDrawable(mWifiActivityBitmap);
	    } else {
	    	mWifiActivity.setImageDrawable(null);	
	    }
            mWifiGroup.setContentDescription(mWifiDescription);
            mWifiGroup.setVisibility(View.VISIBLE);
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Log.d(TAG,
                String.format("wifi: %s sig=%d act=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId, mWifiActivityId));
 	
        if (mMobileVisible && !mIsAirplaneMode) {
            if (mMobileStrengthId != 0) {               
		Drawable mobileBitmap = mContext.getResources().getDrawable(mMobileStrengthId);
                if (mCustomColor) {   
		    mobileBitmap=GrayscaleDrawable(mContext,mobileBitmap); 
		    mobileBitmap.setColorFilter(systemColor, Mode.MULTIPLY);
                } else {
                    mobileBitmap.clearColorFilter();
                }
                mMobile.setImageDrawable(mobileBitmap);
            } else {
	    	mMobile.setImageDrawable(null);
	    }

	    if (mMobileActivityId != 0) {	    		
		Drawable mMobileActivityBitmap = mContext.getResources().getDrawable(mMobileActivityId);
		if (mCustomColor) {
		    mMobileActivityBitmap=GrayscaleDrawable(mContext,mMobileActivityBitmap);
		    mMobileActivityBitmap.setColorFilter(systemColor, Mode.MULTIPLY);
            	} else {
                    mMobileActivityBitmap.clearColorFilter();
            	}
	    	mMobileActivity.setImageDrawable(mMobileActivityBitmap);
	    } else {
	    	mMobileActivity.setImageDrawable(null);
	    }
            	
	    if (mMobileTypeId != 0) {	 		
		Drawable mMobileTypeBitmap = mContext.getResources().getDrawable(mMobileTypeId);   
		if (mCustomColor) {
		    mMobileTypeBitmap=GrayscaleDrawable(mContext,mMobileTypeBitmap);
		    mMobileTypeBitmap.setColorFilter(systemColor, Mode.MULTIPLY);
            	} else {
                    mMobileTypeBitmap.clearColorFilter();
            	}
	    	mMobileType.setImageDrawable(mMobileTypeBitmap);
	    } else {
	    	mMobileType.setImageDrawable(null);
	    }

            mMobileGroup.setContentDescription(mMobileTypeDescription + " " + mMobileDescription);
            mMobileGroup.setVisibility(View.VISIBLE);
            if (mCarrierIconId > 0) {
                mStatusBar.setCarrierImageResource(mCarrierIconId);
                mStatusBar.setCarrierVisibility(View.VISIBLE);
            } else {
                mStatusBar.setCarrierVisibility(View.GONE);
            }
        } else {
            mMobileGroup.setVisibility(View.GONE);
            mStatusBar.setCarrierVisibility(View.GONE);
        }

        if (mIsAirplaneMode) {
            if (mAirplaneIconId != 0) {
                Drawable AirplaneBitmap = mContext.getResources().getDrawable(mAirplaneIconId);
                if (mCustomColor) {
		    AirplaneBitmap=GrayscaleDrawable(mContext,AirplaneBitmap);
		    AirplaneBitmap.setColorFilter(systemColor, Mode.MULTIPLY);
                } else {
                    mAirplane.clearColorFilter();
                }
                mAirplane.setImageDrawable(AirplaneBitmap);
            } else {
                mAirplane.setImageDrawable(null);
	    }
            mAirplane.setVisibility(View.VISIBLE);
        } else {
            mAirplane.setVisibility(View.GONE);
        }

        if (mMobileVisible && mWifiVisible && mIsAirplaneMode) {
            mSpacer.setVisibility(View.INVISIBLE);
        } else {
            mSpacer.setVisibility(View.GONE);
        }

        if (DEBUG) Log.d(TAG,
                String.format("mobile: %s sig=%d act=%d typ=%d",
                    (mMobileVisible ? "VISIBLE" : "GONE"),
                    mMobileStrengthId, mMobileActivityId, mMobileTypeId));

        mMobileType.setVisibility(
                !mWifiVisible ? View.VISIBLE : View.GONE);
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mCustomColor = Settings.System.getIntForUser(resolver,
                Settings.System.CUSTOM_SYSTEM_ICON_COLOR, 0, UserHandle.USER_CURRENT) == 1;
        systemColor = Settings.System.getIntForUser(resolver,
                Settings.System.SYSTEM_ICON_COLOR, -2, UserHandle.USER_CURRENT);
        apply();
    }
}

