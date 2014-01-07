package xu.li.cordova.wechat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.tencent.mm.sdk.openapi.IWXAPI;
import com.tencent.mm.sdk.openapi.SendMessageToWX;
import com.tencent.mm.sdk.openapi.WXAPIFactory;
import com.tencent.mm.sdk.openapi.WXMediaMessage;
import com.tencent.mm.sdk.openapi.WXTextObject;
import com.tencent.mm.sdk.openapi.WXWebpageObject;

public class Wechat extends CordovaPlugin {

	public static final String WXAPPID_PROPERTY_KEY = "wechatappid";

	public static final String ERROR_WX_NOT_INSTALLED = "未安装微信";
	public static final String ERROR_ARGUMENTS = "参数错误";

	public static final String KEY_ARG_MESSAGE = "message";
	public static final String KEY_ARG_SCENE = "scene";
	public static final String KEY_ARG_MESSAGE_TITLE = "title";
	public static final String KEY_ARG_MESSAGE_DESCRIPTION = "description";
	public static final String KEY_ARG_MESSAGE_THUMB = "thumb";
	public static final String KEY_ARG_MESSAGE_MEDIA = "media";
	public static final String KEY_ARG_MESSAGE_MEDIA_TYPE = "type";
	public static final String KEY_ARG_MESSAGE_MEDIA_WEBPAGEURL = "webpageUrl";
	public static final String KEY_ARG_MESSAGE_MEDIA_TEXT = "text";

	public static final int TYPE_WX_SHARING_APP = 1;
	public static final int TYPE_WX_SHARING_EMOTION = 2;
	public static final int TYPE_WX_SHARING_FILE = 3;
	public static final int TYPE_WX_SHARING_IMAGE = 4;
	public static final int TYPE_WX_SHARING_MUSIC = 5;
	public static final int TYPE_WX_SHARING_VIDEO = 6;
	public static final int TYPE_WX_SHARING_WEBPAGE = 7;
	public static final int TYPE_WX_SHARING_TEXT = 8;

	protected IWXAPI wxAPI;
	protected CallbackContext currentCallbackContext;

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

		if (action.equals("share")) {
			// sharing
			return share(args, callbackContext);
		}

		return super.execute(action, args, callbackContext);
	}

	protected IWXAPI getWXAPI() {
		if (wxAPI == null) {
			String appId = webView.getProperty(WXAPPID_PROPERTY_KEY, "");
			wxAPI = WXAPIFactory.createWXAPI(webView.getContext(), appId, true);
		}

		return wxAPI;
	}

	protected boolean share(JSONArray args, CallbackContext callbackContext) throws JSONException {
		final IWXAPI api = getWXAPI();

		api.registerApp(webView.getProperty(WXAPPID_PROPERTY_KEY, ""));

		// check if installed
		if (!api.isWXAppInstalled()) {
			callbackContext.error(ERROR_WX_NOT_INSTALLED);
			return false;
		}

		// check if # of arguments is correct
		if (args.length() != 1) {
			callbackContext.error(ERROR_ARGUMENTS);
		}

		final JSONObject params = args.getJSONObject(0);
		final SendMessageToWX.Req req = new SendMessageToWX.Req();
		req.transaction = String.valueOf(System.currentTimeMillis());

		if (params.has(KEY_ARG_SCENE)) {
			req.scene = params.getInt(KEY_ARG_SCENE);
		} else {
			req.scene = SendMessageToWX.Req.WXSceneTimeline;
		}

		// run in background
		cordova.getThreadPool().execute(new Runnable() {

			@Override
			public void run() {
				try {
					req.message = buildSharingMessage(params.getJSONObject(KEY_ARG_MESSAGE));
				} catch (JSONException e) {
					e.printStackTrace();
				}

				if (api.sendReq(req)) {
					currentCallbackContext.success();
				}
			}

		});

		// save the current callback context
		currentCallbackContext = callbackContext;
		return true;
	}

	protected WXMediaMessage buildSharingMessage(JSONObject message) throws JSONException {
		URL thumbnailUrl = null;
		Bitmap thumbnail = null;

		try {
			thumbnailUrl = new URL(message.getString(KEY_ARG_MESSAGE_THUMB));
			thumbnail = compressImageBySize(BitmapFactory.decodeStream(thumbnailUrl.openConnection().getInputStream()));

		} catch (MalformedURLException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		WXMediaMessage wxMediaMessage = new WXMediaMessage();
		wxMediaMessage.title = message.getString(KEY_ARG_MESSAGE_TITLE);
		wxMediaMessage.description = message.getString(KEY_ARG_MESSAGE_DESCRIPTION);
		if (thumbnail != null) {
			wxMediaMessage.setThumbImage(thumbnail);
		}

		// media parameters
		WXMediaMessage.IMediaObject mediaObject = null;
		JSONObject media = message.getJSONObject(KEY_ARG_MESSAGE_MEDIA);

		// check types
		int type = media.has(KEY_ARG_MESSAGE_MEDIA_TYPE) ? media.getInt(KEY_ARG_MESSAGE_MEDIA_TYPE) : TYPE_WX_SHARING_WEBPAGE;
		switch (type) {
		case TYPE_WX_SHARING_APP:
			break;

		case TYPE_WX_SHARING_EMOTION:
			break;

		case TYPE_WX_SHARING_FILE:
			break;

		case TYPE_WX_SHARING_IMAGE:
			break;

		case TYPE_WX_SHARING_MUSIC:
			break;

		case TYPE_WX_SHARING_VIDEO:
			break;

		case TYPE_WX_SHARING_TEXT:
			mediaObject = new WXTextObject();
			((WXTextObject) mediaObject).text = media.getString(KEY_ARG_MESSAGE_MEDIA_TEXT);
			break;

		case TYPE_WX_SHARING_WEBPAGE:
		default:
			mediaObject = new WXWebpageObject();
			((WXWebpageObject) mediaObject).webpageUrl = media.getString(KEY_ARG_MESSAGE_MEDIA_WEBPAGEURL);
		}

		wxMediaMessage.mediaObject = mediaObject;

		return wxMediaMessage;
	}

	private Bitmap compressImageByQuality(Bitmap image) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		image.compress(Bitmap.CompressFormat.JPEG, 100, baos);// 质量压缩方法，这里100表示不压缩，把压缩后的数据存放到baos中
		int options = 100;
		while (baos.toByteArray().length / 1024 > 32) { // 循环判断如果压缩后图片是否大于32kb,大于继续压缩
			baos.reset();// 重置baos即清空baos
			image.compress(Bitmap.CompressFormat.JPEG, options, baos);// 这里压缩options%，把压缩后的数据存放到baos中
			options -= 10;// 每次都减少10
		}
		ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());// 把压缩后的数据baos存放到ByteArrayInputStream中
		Bitmap bitmap = BitmapFactory.decodeStream(isBm, null, null);// 把ByteArrayInputStream数据生成图片
		return bitmap;
	}

	private Bitmap compressImageBySize(Bitmap image) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
		if (baos.toByteArray().length / 1024 > 1024) {// 判断如果图片大于1M,进行压缩避免在生成图片（BitmapFactory.decodeStream）时溢出
			baos.reset();// 重置baos即清空baos
			image.compress(Bitmap.CompressFormat.JPEG, 50, baos);// 这里压缩50%，把压缩后的数据存放到baos中
		}
		ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());
		BitmapFactory.Options newOpts = new BitmapFactory.Options();
		// 开始读入图片，此时把options.inJustDecodeBounds 设回true了
		newOpts.inJustDecodeBounds = true;
		Bitmap bitmap = BitmapFactory.decodeStream(isBm, null, newOpts);
		newOpts.inJustDecodeBounds = false;
		int w = newOpts.outWidth;
		int h = newOpts.outHeight;

		float hh = 200f;// 这里设置高度为400f
		float ww = 150f;// 这里设置宽度为300f
		// 缩放比。由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
		int be = 1;// be=1表示不缩放
		if (w > h && w > ww) {// 如果宽度大的话根据宽度固定大小缩放
			be = (int) (newOpts.outWidth / ww);
		} else if (w < h && h > hh) {// 如果高度高的话根据宽度固定大小缩放
			be = (int) (newOpts.outHeight / hh);
		}
		if (be <= 0)
			be = 1;
		newOpts.inSampleSize = be;// 设置缩放比例
		// 重新读入图片，注意此时已经把options.inJustDecodeBounds 设回false了
		isBm = new ByteArrayInputStream(baos.toByteArray());
		bitmap = BitmapFactory.decodeStream(isBm, null, newOpts);
		return compressImageByQuality(bitmap);// 压缩好比例大小后再进行质量压缩
	}
}
