import assert from 'node:assert/strict';
import {parseTransactions} from '../assets/local-ledger/parser.mjs';
const now=new Date(2026,0,3,12,0);
const raw=`招商银行信用卡
交易成功提醒
交易时间：尾号1234信用卡1月2日10:00
交易类型：消费-个人消费卡
交易金额：12.34人民币，点击查看详情
交易商户：测试茶饮店
可用额度：￥9000.00
交易成功提醒
交易时间：尾号1234信用卡1月3日11:00
交易类型：消费-个人消费卡
交易金额：56.78人民币，点击查看详情
交易商户：测试超市
可用额度：￥8943.22`;
const rows=parseTransactions(raw,now);
assert.equal(rows.length,2);assert.equal(rows[0].amount,'12.34');assert.equal(rows[1].amount,'56.78');
assert.match(rows[0].merchant,/测试茶饮店/);assert.match(rows[1].happened,/01-03T11:00/);
assert.equal(parseTransactions('记账日报\n昨日支出 ¥300.00\n本月统计 已支出¥500.00 已入账¥200.00',now).length,0);
console.log('PASS synthetic multi-card amount/merchant/time and summary exclusion');
