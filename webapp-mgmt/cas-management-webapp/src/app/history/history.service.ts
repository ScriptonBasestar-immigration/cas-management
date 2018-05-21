/**
 * Created by tschmidt on 2/13/17.
 */
import {Injectable} from '@angular/core';
import {History} from '../../domain/history';
import {Service} from '../service';
import {Http} from '@angular/http';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';

@Injectable()
export class HistoryService extends Service {

  constructor(protected http: HttpClient) {
    super(http);
  }

  history(fileName: string): Observable<History[]> {
    return this.get<History[]>('history?path=' + fileName);
  }

  checkout(id: string, path: String): Observable<String> {
    return this.getText('checkout?id=' + id + '&path=' + path);
  }

}
