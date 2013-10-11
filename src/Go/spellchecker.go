package main

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"net/url"
	"runtime"
	"time"
)

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

type Candidate struct {
	Word string
	Prob int
}

func main() {
	http.HandleFunc("/", func(writer http.ResponseWriter, r *http.Request) {
		st := time.Now().Nanosecond()
		jsonStr := "[]"
		if r.Method == "POST" {
			r.ParseForm()
			if candidates, found := r.Form["candidate"]; found && len(candidates) > 0 {
				results := GetCandidates(candidates)

				bjson, _ := json.Marshal(results)
				jsonStr = string(bjson)
			}
		}

		et := time.Now().Nanosecond()
		ctime := float64(et-st) / 1000000.0
		ret := fmt.Sprintf("{\"response\":%s, \"time\":%f}", jsonStr, ctime)
		fmt.Fprint(writer, ret)
		fmt.Println(ret)
	})
	fmt.Println("http listene :9090")
	if err := http.ListenAndServe(":9090", nil); err != nil {
		log.Fatal("failed to start server ", err)
	}
}

func GetCandidates(candidates []string) []Candidate {
	workers := runtime.NumCPU()
	jobs := make(chan string, workers)
	runtime.GOMAXPROCS(workers)

	res := make(chan Candidate, len(candidates))
	done := make(chan int, workers)

	for i := 0; i < workers; i++ {
		go func() {
			for str := range jobs {
				prob := GetProbability(str)
				res <- Candidate{str, prob}
			}
			done <- 1
		}()
	}
	for _, s := range candidates {
		jobs <- s
	}
	close(jobs)
	for i := 0; i < workers; i++ {
		<-done
	}
	close(done)
	close(res)

	results := make([]Candidate, 0, len(res))
	for r := range res {
		if r.Prob > 1 {
			results, _ = AddCandidate(results, r)
		}

	}

	return results
}

func AddCandidate(candidates []Candidate, oneCddt Candidate) ([]Candidate, error) {
	if len(candidates) == 0 {
		candidates = append(candidates, oneCddt)
	} else {
		for k, v := range candidates {
			if oneCddt.Prob > v.Prob {
				candidates = append(candidates, Candidate{})
				copy(candidates[k+1:], candidates[k:])
				candidates[k] = oneCddt
			}
		}
	}
	return candidates, nil
}

func GetProbability(word string) int {
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

func GetNumFound(result []byte) int {
	var x ResponseJSON
	json.Unmarshal(result, &x)

	return x.Response.NumFound
}
