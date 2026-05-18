"use strict";
import type { Uni } from "@dcloudio/types";

const install = (uni: Uni) => {
  uni.showLoading = function (options: any) {
    const defaultOptions = {
      mask: true
    };
    return new Promise((resolve, reject) => {
      if (typeof wx.showLoading === "function") {
        wx.showLoading(Object.assign(defaultOptions, options));
        resolve(null);
      } else {
        reject(new Error("showLoading is not supported"));
      }
    });
  };
};

export default { install };
