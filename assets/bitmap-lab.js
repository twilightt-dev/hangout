(() => {
  'use strict';
  const lab = document.querySelector('[data-bitmap-lab]');
  if (lab) {
    const key = lab.dataset.key;
    const bits = Array(32).fill(0);
    let selected = 8;
    let bytes = 0;
    let repeatedEighth = false;
    const days = lab.querySelector('[data-days]');
    const groups = lab.querySelector('[data-bytes]');
    const output = (name, text) => { lab.querySelector(`[data-${name}]`).textContent = text; };
    const dayButtons = Array.from({ length:31 }, (_, index) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'day';
      button.innerHTML = `<span>${index + 1} 日</span><strong>0</strong>`;
      button.addEventListener('click', () => { selected = index + 1; render(); });
      days.append(button);
      return button;
    });
    const pad = document.createElement('span');
    pad.className = 'calendar-pad';
    pad.textContent = '填充位';
    days.append(pad);
    function render() {
      dayButtons.forEach((button,index) => {
        button.classList.toggle('is-on',bits[index] === 1);
        button.classList.toggle('is-selected',selected === index + 1);
        button.querySelector('strong').textContent = bits[index];
        button.setAttribute('aria-pressed',String(selected === index + 1));
        button.setAttribute('aria-label',`${index + 1} 日，${bits[index] ? '已签到' : '未签到'}，选择该日`);
      });
      output('selection',`第 ${selected} 天 → offset = ${selected - 1}`);
      output('count',String(bits.reduce((sum,bit) => sum + bit,0)));
      output('size',`${bytes} 字节${bytes ? '（仅 String 数据长度）' : '（key 尚未创建）'}`);
      output('read',`GETBIT ${key} ${selected - 1}\n→ ${bits[selected - 1]}\nBITCOUNT ${key}\n→ ${bits.reduce((sum,bit) => sum + bit,0)}\nTYPE ${key}\n→ ${bytes ? 'string' : 'none'}`);
      groups.replaceChildren();
      for (let byte = 0; byte < 4; byte++) {
        const section = document.createElement('div');
        section.className = `byte-group${byte >= bytes ? ' unallocated' : ''}`;
        const slice = bits.slice(byte * 8,byte * 8 + 8);
        const value = parseInt(slice.join(''),2);
        section.innerHTML = `<div class="byte-label"><span>字节 ${byte} · offset ${byte * 8}～${byte * 8 + 7}</span><span>${byte < bytes ? '已分配' : '未分配'}</span></div><div class="bit-strip">${slice.map((bit,index) => `<span class="${bit ? 'one' : ''} ${selected - 1 === byte * 8 + index ? 'selected' : ''}">${bit}</span>`).join('')}</div><div class="byte-label"><span>高位 → 低位</span><span>0x${value.toString(16).toUpperCase().padStart(2,'0')}</span></div>`;
        groups.append(section);
      }
      const exact = bits.every((bit,index) => bit === ([0,2,7].includes(index) ? 1 : 0));
      output('task',exact && repeatedEighth ? '完成！1、3、8 日共签到 3 天；重复签到第 8 天返回旧值 1，第一个字节是 10100001（0xA1）。' : exact ? '已签到 1、3、8 日。再对第 8 天执行一次「签到」，观察返回值。' : '练习：签到第 1、3、8 天，再重复签到第 8 天。观察计数和返回值。');
    }
    lab.querySelectorAll('[data-set-value]').forEach(button => {
      button.addEventListener('click',() => {
        const offset = selected - 1;
        const value = Number(button.dataset.setValue);
        const old = bits[offset];
        bits[offset] = value;
        bytes = Math.max(bytes,Math.floor(offset / 8) + 1);
        if (selected === 8 && old === 1 && value === 1) repeatedEighth = true;
        output('operation',`SETBIT ${key} ${offset} ${value}\n→ ${old}（返回修改前的值；当前值为 ${value}）`);
        render();
      });
    });
    render();
  }
  document.querySelectorAll('[data-quiz]').forEach(quiz => {
    quiz.querySelectorAll('button[data-correct]').forEach(button => {
      button.addEventListener('click',() => {
        const correct = button.dataset.correct === 'true';
        quiz.querySelector('[data-quiz-feedback]').textContent = correct ? quiz.dataset.explanation : '再想一想。先从日期映射、返回旧值或每字节 8 bit 的规则推导。';
      });
    });
  });
})();
