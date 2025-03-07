// import http from 'k6/http';
// import { sleep } from 'k6';
//
// export let options = {
//     vus: 100,  // 100명의 가상 사용자
//     duration: '1s',  // 1초 동안
// };
//
// export default function () {
//     let res = http.get('https://v6.exchangerate-api.com/v6/9054f41e7e8b6358d04ec354/latest/KWD');  // 외부 API 호출
//     console.log(`Response time: ${res.timings.duration}ms`);  // 응답 시간 출력
// }
