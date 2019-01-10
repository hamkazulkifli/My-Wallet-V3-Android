package mobi.lab.veriff.sample.service;

import mobi.lab.veriff.data.VeriffConstants;
import mobi.lab.veriff.service.VeriffStatusUpdatesService;

public class VeriffLibraryStatusUpdatesService extends VeriffStatusUpdatesService {

    @Override
    protected void onStatusChanged(String sessionToken, int statusCode) {
        if (statusCode == VeriffConstants.STATUS_USER_FINISHED) {
            //user finished whatever he/she was asked to do, there might be other callbacks coming after this one (for example if the images are still being uploaded in the background)
        } else if (statusCode == VeriffConstants.STATUS_ERROR_NO_IDENTIFICATION_METHODS_AVAILABLE) {
            //there are no identifications methods currently available
        } else if (statusCode == VeriffConstants.STATUS_ERROR_SETUP) {
            //issue with the provided vendor data
        } else if (statusCode == VeriffConstants.STATUS_ERROR_UNKNOWN) {
            //unidentified error
        } else if (statusCode == VeriffConstants.STATUS_ERROR_NETWORK) {
            //network unavailable
        } else if (statusCode == VeriffConstants.STATUS_USER_CANCELED) {
            //user closed library
        } else if (statusCode == VeriffConstants.STATUS_UNABLE_TO_ACCESS_CAMERA) {
            //we are unable to access phone camera (either access denied or there are no usable cameras)
        } else if (statusCode == VeriffConstants.STATUS_UNABLE_TO_RECORD_AUDIO) {
            //we are unable to access phone microphone
        } else if (statusCode == VeriffConstants.STATUS_SUBMITTED) {
            //SelfID photos were successfully uploaded
        } else if (statusCode == VeriffConstants.STATUS_OUT_OF_BUSINESS_HOURS) {
            //call was made out of business hours, there were no verification specialists to handle the request
        } else if (statusCode == VeriffConstants.STATUS_ERROR_SESSION) {
            //invalid sessionToken was passed to the library
        } else if (statusCode == VeriffConstants.STATUS_DONE) {
            //verification specialist declined the session
        } else if (statusCode == VeriffConstants.STATUS_VIDEO_CALL_ENDED) {
            //video call flow was finished
        }
    }

}