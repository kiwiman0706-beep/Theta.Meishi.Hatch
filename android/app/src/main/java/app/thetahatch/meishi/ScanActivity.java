package app.thetahatch.meishi;

// "撮って登録" for the other series apps (Theta.Hatch.Series spec §6.2 SCAN):
// Intent("app.thetahatch.action.SCAN"), extras source (the caller's catalog id) and mode
// (camera, the default, or pick). The whole Meishi screen in its own instance, which the caller can
// wait on (MainActivity is singleTask and could not give a result): the camera opens at once, the
// card is read and reviewed here, and saving it ends with RESULT_OK; leaving ends with
// RESULT_CANCELED. MainActivity does the work (scanCall).
public class ScanActivity extends MainActivity {}
