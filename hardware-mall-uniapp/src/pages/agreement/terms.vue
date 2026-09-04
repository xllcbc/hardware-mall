<template>
  <view class="terms-container">
    <view class="terms-content">
      <view class="section">
        <text class="title">用户服务协议</text>
        <text class="paragraph">欢迎使用五金商城小程序。在使用本小程序前，请您仔细阅读并充分理解本协议。您点击"同意"、登录或继续使用本小程序服务，即视为您已阅读并同意本协议的全部内容。</text>
      </view>

      <view class="section">
        <text class="sub-title">一、账号注册与使用</text>
        <text class="paragraph">您通过授权登录本小程序，应保证所提供信息的真实、准确、合法。您的账号仅供您本人使用，不得出借、转让。</text>
      </view>

      <view class="section">
        <text class="sub-title">二、商品信息</text>
        <text class="paragraph">本小程序展示的商品信息（包括价格、库存、图片、规格等）仅供参考，具体以您下单时确认为准。我们会尽力保证商品信息的准确，但因商品种类繁多、信息更新存在延迟，可能出现与实物略有差异的情况。</text>
      </view>

      <view class="section">
        <text class="sub-title">三、订单与支付</text>
        <text class="paragraph">您在确认订单并完成支付后，订单即告成立。请您在提交订单前核对商品信息、收货地址及联系方式。订单支付通过微信支付完成，具体支付规则以微信支付相关协议为准。</text>
      </view>

      <view class="section">
        <text class="sub-title">四、配送与收货</text>
        <text class="paragraph">我们将按照您填写的收货地址安排配送。请您保持联系方式畅通并及时收货。因您提供的地址或联系方式错误导致的损失，由您自行承担。</text>
      </view>

      <view class="section">
        <text class="sub-title">五、退换货与退款</text>
        <text class="paragraph">如商品存在质量问题或与描述不符，您可按照本小程序公示的规则申请退换货或退款。我们将依法依约为您处理相关售后问题。</text>
      </view>

      <view class="section">
        <text class="sub-title">六、用户行为规范</text>
        <text class="paragraph">您在使用本小程序时，不得从事任何违法、违规或损害他人权益的行为，包括但不限于恶意下单、刷单、恶意差评、攻击系统等。如发现上述行为，我们有权采取限制使用、取消订单等措施。</text>
      </view>

      <view class="section">
        <text class="sub-title">七、知识产权与免责声明</text>
        <text class="paragraph">本小程序的内容（包括文字、图片、界面设计等）受法律保护。因不可抗力、网络故障、第三方原因等导致的服务中断或异常，我们不承担相应责任，但将尽力减少对您的影响。</text>
      </view>

      <view class="section">
        <text class="sub-title">八、协议变更与联系我们</text>
        <text class="paragraph">我们可能根据业务需要适时修订本协议，修订后的协议将在小程序内公示。如您对本协议有任何疑问，可通过邮箱 3316377141@qq.com 与我们联系。</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
</script>

<style scoped>
.terms-container {
  min-height: 100vh;
  background: #fff;
  padding: 32rpx;
  box-sizing: border-box;
}

.terms-content {
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
