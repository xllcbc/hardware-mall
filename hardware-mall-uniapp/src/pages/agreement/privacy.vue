<template>
  <view class="privacy-container">
    <view class="privacy-content">
      <view class="section">
        <text class="title">隐私政策</text>
        <text class="paragraph">本小程序（五金商城）尊重并保护您的个人信息。在使用本小程序前，请您仔细阅读并充分理解本隐私政策。您点击"同意"、登录或继续使用本小程序服务，即视为您已阅读并同意本政策的全部内容。</text>
      </view>

      <view class="section">
        <text class="sub-title">一、我们收集的信息</text>
        <text class="paragraph">为了向您提供服务，我们会在征得您明示同意后，收集以下必要信息：</text>
        <text class="paragraph">1. 微信昵称、头像：用于展示用户身份信息，完善个人资料。</text>
        <text class="paragraph">2. 手机号：用于账号登录注册，以及订单配送联系与售后服务。</text>
        <text class="paragraph">3. 收货地址：用于商品配送，填写收货地址以完成订单发货。</text>
        <text class="paragraph">4. 订单信息：用于生成订单、处理发货、退款及售后查询。</text>
        <text class="paragraph">5. 选中的照片或视频信息：用于用户自定义头像（从相册选择或拍照上传）。</text>
      </view>

      <view class="section">
        <text class="sub-title">二、信息的使用</text>
        <text class="paragraph">我们仅在本政策明示的用途内使用您的信息，不会超范围使用。若使用目的或范围发生变更，我们会再次征得您的明示同意。</text>
      </view>

      <view class="section">
        <text class="sub-title">三、信息的存储</text>
        <text class="paragraph">除法律法规另有规定外，我们对您信息的保存期限为实现处理目的所必要的最短时间。我们会采取合理的安全措施保护您的信息。</text>
      </view>

      <view class="section">
        <text class="sub-title">四、信息的对外提供</text>
        <text class="paragraph">我们不会主动共享或转让您的信息至任何第三方，也不会对外公开披露您的信息，法律法规另有规定或经您单独同意的除外。为实现支付、图片存储等必要功能，我们会委托具备资质的第三方服务商处理相关信息，并要求其依法保护您的信息。</text>
      </view>

      <view class="section">
        <text class="sub-title">五、您的权利</text>
        <text class="paragraph">您有权查阅、复制、更正、删除您的个人信息。若您注册了账号，可通过下方联系方式申请注销账号，我们承诺在十五个工作日内完成核查和处理。</text>
      </view>

      <view class="section">
        <text class="sub-title">六、未成年人保护</text>
        <text class="paragraph">若您是14周岁以下的未成年人，需在监护人陪同下阅读本政策，并在征得监护人明示同意后使用本小程序服务。</text>
      </view>

      <view class="section">
        <text class="sub-title">七、联系我们</text>
        <text class="paragraph">如您对本政策或个人信息保护有任何疑问、建议或投诉，可通过以下方式与我们联系：邮箱 3316377141@qq.com。</text>
        <text class="paragraph">本政策自发布之日起生效，如有更新，我们会以适当方式通知您。</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
</script>

<style scoped>
.privacy-container {
  min-height: 100vh;
  background: #fff;
  padding: 32rpx;
  box-sizing: border-box;
}

.privacy-content {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.title {
  font-size: 40rpx;
  font-weight: 700;
  color: #333;
  text-align: center;
}

.sub-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #333;
}

.paragraph {
  font-size: 26rpx;
  line-height: 1.8;
  color: #666;
  text-align: justify;
}
</style>
