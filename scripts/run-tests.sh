#!/bin/bash
# 运行 Apifox CLI 测试脚本
# 使用方式: ./scripts/run-tests.sh [options]
#
# 选项:
#   --all        运行所有测试集 (默认)
#   --auth       只运行认证模块测试
#   --product    只运行商品模块测试
#   --cart       只运行购物车模块测试
#   --order      只运行订单模块测试
#   --scenario   只运行完整购买流程场景
#   --help       显示帮助信息

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APIFOX_DIR="$SCRIPT_DIR/../apifox"
CONFIG_FILE="$APIFOX_DIR/apifox-cli-config.json"

echo "=========================================="
echo "  五金商城 - Apifox CLI 自动化测试"
echo "=========================================="

# 检查 apifox-cli 是否已安装
if ! command -v apifox &> /dev/null; then
    echo ""
    echo "⚠️  警告: apifox-cli 未安装"
    echo ""
    echo "请先安装 apifox-cli:"
    echo "  npm install -g apifox-cli"
    echo ""
    exit 1
fi

# 检查配置文件是否存在
if [ ! -f "$CONFIG_FILE" ]; then
    echo "❌ 错误: 找不到配置文件: $CONFIG_FILE"
    exit 1
fi

echo "✓ Apifox CLI 已安装"
echo "✓ 配置文件已找到: $CONFIG_FILE"

# 解析命令行参数
TEST_MODE="${1:--all}"

case "$TEST_MODE" in
    --all)
        echo ""
        echo "运行模式: 所有测试集"
        echo ""
        echo "开始运行测试..."
        apifox run \
            --config "$CONFIG_FILE" \
            --testSets "$APIFOX_DIR/test-sets" \
            --output "$APIFOX_DIR/reports"
        ;;
    --auth)
        echo ""
        echo "运行模式: 认证模块测试"
        echo ""
        apifox run \
            --config "$CONFIG_FILE" \
            --testSets "$APIFOX_DIR/test-sets/01-auth.yml" \
            --output "$APIFOX_DIR/reports"
        ;;
    --category)
        echo ""
        echo "运行模式: 分类模块测试"
        echo ""
        apifox run \
            --config "$CONFIG_FILE" \
            --testSets "$APIFOX_DIR/test-sets/02-category.yml" \
            --output "$APIFOX_DIR/reports"
        ;;
    --product)
        echo ""
        echo "运行模式: 商品模块测试"
        echo ""
        apifox run \
            --config "$CONFIG_FILE" \
            --testSets "$APIFOX_DIR/test-sets/03-product.yml" \
            --output "$APIFOX_DIR/reports"
        ;;
    --cart)
        echo ""
        echo "运行模式: 购物车模块测试"
        echo ""
        apifox run \
            --config "$CONFIG_FILE" \
            --testSets "$APIFOX_DIR/test-sets/04-cart.yml" \
            --output "$APIFOX_DIR/reports"
        ;;
    --order)
        echo ""
        echo "运行模式: 订单模块测试"
        echo ""
        apifox run \
            --config "$CONFIG_FILE" \
            --testSets "$APIFOX_DIR/test-sets/05-order.yml" \
            --output "$APIFOX_DIR/reports"
        ;;
    --address)
        echo ""
        echo "运行模式: 地址模块测试"
        echo ""
        apifox run \
            --config "$CONFIG_FILE" \
            --testSets "$APIFOX_DIR/test-sets/06-address.yml" \
            --output "$APIFOX_DIR/reports"
        ;;
    --logistics)
        echo ""
        echo "运行模式: 物流模块测试"
        echo ""
        apifox run \
            --config "$CONFIG_FILE" \
            --testSets "$APIFOX_DIR/test-sets/07-logistics.yml" \
            --output "$APIFOX_DIR/reports"
        ;;
    --scenario)
        echo ""
        echo "运行模式: 完整购买流程场景"
        echo ""
        apifox run \
            --config "$CONFIG_FILE" \
            --testSets "$APIFOX_DIR/test-scenarios/full-purchase-flow.yml" \
            --output "$APIFOX_DIR/reports"
        ;;
    --help)
        echo ""
        echo "使用方法: $0 [options]"
        echo ""
        echo "选项:"
        echo "  --all        运行所有测试集 (默认)"
        echo "  --auth       只运行认证模块测试"
        echo "  --category   只运行分类模块测试"
        echo "  --product    只运行商品模块测试"
        echo "  --cart       只运行购物车模块测试"
        echo "  --order      只运行订单模块测试"
        echo "  --address    只运行地址模块测试"
        echo "  --logistics  只运行物流模块测试"
        echo "  --scenario   只运行完整购买流程场景"
        echo "  --help       显示帮助信息"
        echo ""
        exit 0
        ;;
    *)
        echo "❌ 错误: 未知选项: $TEST_MODE"
        echo "使用 --help 查看帮助信息"
        exit 1
        ;;
esac

echo ""
echo "=========================================="
echo "  测试完成!"
echo "=========================================="
echo ""
echo "报告文件位置: $APIFOX_DIR/reports"
echo ""
