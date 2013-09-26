package main

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/url"
	"runtime"
	"strings"
	"time"
)

var workers = runtime.NumCPU()
var jobs = make(chan string, workers)

func main() {
	st := time.Now().Nanosecond()
	runtime.GOMAXPROCS(runtime.NumCPU())
	ps := "大家 皇家 陆家 哪家 娘家 老家 你家 买家 冤家 厂家 回家 归家 当家 四家 独家 住家 农家 新家 渔家 方家 还家 这家 作家 佛家 首家 之家 国家 想家 多家 居家 举家 发家 王家 婆家 身家 几家 她家 两家 学家 某家 东家 张家 专家 酒家 世家 三家 小家 下家 玩家 一家 输家 炒家 万家 道家 那家 周家 安家 画家 分家 看家 宜家 搬家 他家 每家 儒家 赢家 别家 私家 客家 如家 赵家 仇家 到家 起家 商家 家家 顾家 卖家 亲家 富家 岳家 土家 兵家 店家 法家 人家 养家 行家 谁家 庄家 千家 离家 十家 全家 八家 六家 名家 杜家 五家 败家 合家 各家 管家 在家 爱家 我家 百家 李家 成家 马家 自家"
	prefix := strings.Split(ps, " ")
	ow := "税务总局"

	res := make(chan map[string]int, len(prefix))
	done := make(chan int, workers)

	for i := 0; i < workers; i++ {
		go func() {
			for str := range jobs {
				prob := getProbability(str)
				res <- map[string]int{str: prob}
			}
			done <- 1
		}()
	}
	for _, s := range prefix {
		jobs <- s + ow
	}
	close(jobs)
	for i := 0; i < workers; i++ {
		<-done
	}
	close(done)
	close(res)
	results := make([]map[string]int, len(res))
	for r := range res {
		results = append(results, r)
		fmt.Println(r)
	}
	et := time.Now().Nanosecond()
	fmt.Println(float64(et-st)/1000000.0, "ms")
}

func getProbability(word string) int {
	uri := "http://10.123.4.210:8080/solr/termrelated/select?q=text%3A%22" + url.QueryEscape(word) + "%22&start=0&rows=0&wt=json&indent=true"
	resp, err := http.Get(uri)
	if err != nil {
		return 0
	}
	defer resp.Body.Close()
	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return 0
	}
	numFound := GetNumFound(body)

	return numFound
}

type ResponseJSON struct {
	Response Response
}

type ResponseHeader struct {
	QTime  int
	Params Params
}

type Params struct {
	Q string
}

type Response struct {
	NumFound int
}

func GetNumFound(result []byte) int {
	var x ResponseJSON
	json.Unmarshal(result, &x)

	return x.Response.NumFound
}
