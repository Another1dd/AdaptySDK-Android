package com.adapty.api.requests

import com.adapty.api.entity.validate.DataRestoreReceiptReq
import com.adapty.api.entity.validate.DataValidateReceiptReq
import com.google.gson.annotations.SerializedName

class ValidateReceiptRequest {
    @SerializedName("data")
    var data: DataValidateReceiptReq? = null

}