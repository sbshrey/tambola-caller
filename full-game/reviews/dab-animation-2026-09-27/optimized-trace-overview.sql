SELECT p.pid,t.name AS thread,s.name,count(*) AS samples,
       round(sum(s.dur)/1e6,1) AS total_ms,round(avg(s.dur)/1e6,2) AS mean_ms,
       round(max(s.dur)/1e6,2) AS max_ms
FROM slice s JOIN thread_track tt ON tt.id=s.track_id
JOIN thread t ON t.utid=tt.utid JOIN process p ON p.upid=t.upid
WHERE p.name='io.github.sbshrey.tambola.game' AND s.dur>0
GROUP BY p.pid,t.name,s.name ORDER BY total_ms DESC LIMIT 40;
